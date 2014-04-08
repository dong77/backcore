/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.routing._
import akka.contrib.pattern.ClusterSingletonManager
import akka.io.IO
import akka.routing._
import com.typesafe.config.Config
import org.slf4s.Logging
import org.apache.commons.io.IOUtils
import scala.collection.mutable.ListBuffer
import spray.can.Http
import com.coinport.coinex.accounts._
import com.coinport.coinex.apiauth._
import com.coinport.coinex.common._
import com.coinport.coinex.data._
import com.coinport.coinex.mail._
import com.coinport.coinex.markets._
import com.coinport.coinex.metrics._
import com.coinport.coinex.monitoring._
import com.coinport.coinex.robot._
import com.coinport.coinex.users._
import com.coinport.coinex.dw._
import com.coinport.coinex.util._
import Implicits._
import scala.collection.mutable.ListBuffer
import com.coinport.coinex.common._
import ConstantRole._
import MarketRole._
import com.mongodb.casbah._
import com.coinport.coinex.fee.FeeConfig
import com.coinport.coinex.fee.CountFeeSupport
import com.twitter.util.Eval
import java.io.File
import java.io.InputStream
import com.coinport.coinex.ot._

class Deployer(config: Config, hostname: String, markets: Seq[MarketSide])(implicit cluster: Cluster) extends Object with Logging {
  implicit val system = cluster.system
  val paths = new ListBuffer[String]
  val secret = config.getString("akka.exchange.secret")
  val userManagerSecret = MHash.sha256Base64(secret + "userProcessorSecret")
  val apiAuthSecret = MHash.sha256Base64(secret + "apiAuthSecret")

  val mongoUriForViews = MongoURI(config.getString("akka.exchange.mongo-uri-for-views"))
  val mongoForViews = MongoConnection(mongoUriForViews)
  val dbForViews = mongoForViews(mongoUriForViews.database.get)

  val mongoUriForEventExport = MongoURI(config.getString("akka.exchange.mongo-uri-for-event-export"))
  val mongoForEventExport = MongoConnection(mongoUriForEventExport)
  val dbForEventExport = mongoForEventExport(mongoUriForEventExport.database.get)

  def shutdown() {
    mongoForViews.close()
    mongoForEventExport.close()
  }

  def deploy(): LocalRouters = {
    val feeConfig = loadFeeConfig(config.getString("akka.exchange.fee-rules-path"))

    deployMailer(mailer<<)

    // Deploy views first
    markets foreach { m =>
      deployView(Props(new MarketDepthView(m)), market_depth_view << m)
      deployView(Props(new CandleDataView(m)), candle_data_view << m)
      deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_mp_" + m.asString)), market_processor_event_export << m)
    }

    deployView(Props(new UserView(userManagerSecret)), user_view <<)
    deployView(Props(new UserWriter(dbForViews, userManagerSecret)), user_mongo_writer<<)
    deployView(Props(new AccountView(feeConfig)), account_view <<)
    deployView(Props(new MetricsView), metrics_view <<)
    deployView(Props(new ApiAuthView(apiAuthSecret)), api_auth_view <<)

    deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_up")), user_processor_event_export<<)
    deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_ap")), account_processor_event_export<<)
    deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_dwp")), dw_processor_event_export<<)
    deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_mup")), market_update_processor_event_export<<)

    deployView(Props(new TransactionReader(dbForViews)), transaction_mongo_reader<<)
    deployView(Props(new TransactionWriter(dbForViews)), transaction_mongo_writer<<)
    deployView(Props(new OrderReader(dbForViews)), order_mongo_reader<<)
    deployView(Props(new OrderWriter(dbForViews)), order_mongo_writer<<)

    // Then deploy routers
    val routers = new LocalRouters(markets)

    // Finally deploy processors
    markets foreach { m =>
      val props = Props(new MarketProcessor(m,
        routers.accountProcessor.path,
        routers.marketUpdateProcessor.path,
        routers.order_writer,
        routers.transaction_writer) with Commandsourced[MarketState, MarketManager])
      deployProcessor(props, market_processor << m)
    }

    deployProcessor(Props(new MarketUpdateProcessor()), market_update_processor <<)
    deployProcessor(Props(new UserProcessor(routers.mailer, userManagerSecret) with Eventsourced[UserState, UserManager]), user_processor <<)
    deployProcessor(Props(new AccountProcessor(routers.marketProcessors, routers.depositWithdrawProcessor.path, feeConfig) with Eventsourced[AccountState, AccountManager]), account_processor <<)
    deployProcessor(Props(new ApiAuthProcessor(apiAuthSecret) with Commandsourced[ApiSecretState, ApiAuthManager]), api_auth_processor <<)
    deployProcessor(Props(new RobotProcessor(routers) with Commandsourced[RobotState, RobotManager]), robot_processor <<)
    deployProcessor(Props(new DepositWithdrawProcessor(dbForViews, routers.accountProcessor.path)), dw_processor <<)

    // Deploy monitor at last
    deployMonitor(routers)

    routers
  }

  private def deployProcessor(props: Props, name: String) =
    if (cluster.selfRoles.contains(name)) {
      val actor = system.actorOf(ClusterSingletonManager.props(
        singletonProps = props,
        singletonName = "singleton",
        terminationMessage = PoisonPill,
        role = Some(name)),
        name = name)
      paths += actor.path.toString + "/singleton"
    }

  private def deployView(props: Props, name: String) =
    if (cluster.selfRoles.contains(name)) {
      val actor = system.actorOf(props, name)
      paths += actor.path.toString
    }

  private def deployMailer(name: String) = {
    if (cluster.selfRoles.contains(name)) {
      val mandrilApiKey = config.getString("akka.exchange.mailer.mandrill-api-key")
      val handler = new MandrillMailHandler(mandrilApiKey)
      val props = Props(new Mailer(handler))
      val actor = system.actorOf(FromConfig.props(props), name)
      paths += actor.path.toString
    }
  }

  private def deployMonitor(routers: LocalRouters) = {
    val service = system.actorOf(Props(new Monitor(paths.toList)), "monitor-service")
    val port = config.getInt("akka.exchange.monitor.http-port")
    IO(Http) ! Http.Bind(service, hostname, port)
    log.info("Started HTTP server: http://" + hostname + ":" + port)
  }

  private def loadFeeConfig(feeConfigPath: String): FeeConfig = {
    val in: InputStream = this.getClass.getClassLoader.getResourceAsStream(feeConfigPath)
    (new Eval()(IOUtils.toString(in))).asInstanceOf[FeeConfig]
  }
}
