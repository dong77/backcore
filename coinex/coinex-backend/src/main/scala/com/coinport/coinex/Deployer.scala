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
import com.mongodb.casbah._
import com.coinport.coinex.fee.FeeConfig
import com.coinport.coinex.fee.CountFeeSupport
import com.twitter.util.Eval
import java.io.File

class Deployer(config: Config, hostname: String, markets: Seq[MarketSide])(implicit cluster: Cluster) extends Object with Logging {
  implicit val system = cluster.system
  val paths = new ListBuffer[String]
  val secret = config.getString("akka.exchange.secret")
  val userManagerSecret = Hash.sha256Base64(secret + "userProcessorSecret")
  val apiAuthSecret = Hash.sha256Base64(secret + "apiAuthSecret")

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
    import LocalRouters._
    val feeConfig = loadFeeConfig(config.getString("akka.exchange.fee-rules-path"))

    deployMailer(MAILER)

    // Deploy views first
    markets foreach { m =>
      deployView(Props(new MarketDepthView(m)), MARKET_DEPTH_VIEW(m))
      deployView(Props(new CandleDataView(m)), CANDLE_DATA_VIEW(m))
      deployView(Props(new TransactionView(m, dbForViews)), TRANSACTION_VIEW(m))
      deployView(Props(new OrderView(m, dbForViews)), ORDER_VIEW(m))
      deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_mp_" + m.asString)), MARKET_PROCESSOR_EVENT_EXPORT(m))
    }

    deployView(Props(new UserView(userManagerSecret)), USER_VIEW)
    deployView(Props(new UserMPView(dbForViews, userManagerSecret)), USER_MPVIEW)
    deployView(Props(new AccountView(feeConfig)), ACCOUNT_VIEW)
    deployView(Props(new MetricsView), ROBOT_METRICS_VIEW)
    deployView(Props(new ApiAuthView(apiAuthSecret)), API_AUTH_VIEW)

    deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_up")), USER_PROCESSOR_EVENT_EXPORT)
    deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_ap")), ACCOUNT_PROCESSOR_EVENT_EXPORT)
    deployView(Props(new EventExportToMongoView(dbForEventExport, "coinex_dwp")), DEPOSIT_WITHDRAW_PROCESSOR_EVENT_EXPORT)

    // Then deploy routers
    val routers = new LocalRouters(markets)

    // Finally deploy processors
    markets foreach { m =>
      val props = Props(new MarketProcessor(m,
        routers.accountProcessor.path,
        routers.marketUpdateProcessor.path) with Commandsourced[MarketState, MarketManager])
      deployProcessor(props, MARKET_PROCESSOR(m))
    }

    deployProcessor(Props(new MarketUpdateProcessor()), MARKET_UPDATE_PROCESSOR)
    deployProcessor(Props(new UserProcessor(routers.mailer, userManagerSecret) with Eventsourced[UserState, UserManager]), USER_PROCESSOR)
    deployProcessor(Props(new AccountProcessor(routers.marketProcessors, routers.depositWithdrawProcessor.path, feeConfig) with Eventsourced[AccountState, AccountManager]), ACCOUNT_PROCESSOR)
    deployProcessor(Props(new ApiAuthProcessor(apiAuthSecret) with Commandsourced[ApiSecretState, ApiAuthManager]), API_AUTH_PROCESSOR)
    deployProcessor(Props(new RobotProcessor(routers) with Commandsourced[RobotState, RobotManager]), ROBOT_PROCESSOR)
    deployProcessor(Props(new DepositWithdrawProcessor(dbForViews, routers.accountProcessor.path)), DEPOSIT_WITHDRAWAL_PROCESSOR)

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
    val fullPath = this.getClass.getClassLoader.getResource(feeConfigPath).getPath()
    (new Eval()(new File(fullPath))).asInstanceOf[FeeConfig]
  }
}
