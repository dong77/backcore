/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonManager
import akka.cluster.routing._
import akka.routing._
import org.slf4s.Logging
import com.coinport.coinex.accounts._
import com.coinport.coinex.data._
import com.coinport.coinex.markets._
import com.coinport.coinex.robot._
import com.coinport.coinex.users._
import com.coinport.coinex.mail._
import com.coinport.coinex.apiauth._
import com.coinport.coinex.monitoring._
import akka.io.IO
import spray.can.Http
import com.typesafe.config.Config
import Implicits._
import scala.collection.mutable.ListBuffer

class Deployer(config: Config, hostname: String, markets: Seq[MarketSide])(implicit cluster: Cluster) extends Object with Logging {
  implicit val system = cluster.system
  val paths = new ListBuffer[String]

  def deploy() = {
    import LocalRouters._

    val secret = config.getString("akka.exchange.secret")
    val userManagerSecret = util.Hash.sha256Base64(secret + "userProcessorSecret")
    val apiAuthSecret = util.Hash.sha256Base64(secret + "apiAuthSecret")

    deployMailer(MAILER)

    // Deploy views first 
    markets foreach { m =>
      deployView(Props(new MarketDepthView(m)), MARKET_DEPTH_VIEW(m))
      deployView(Props(new CandleDataView(m)), CANDLE_DATA_VIEW(m))
      deployView(Props(new TransactionDataView(m)), TRANSACTION_DATA_VIEW(m))
      deployView(Props(new UserTransactionView(m)), USER_TRANSACTION_VIEW(m))
    }

    deployView(Props(classOf[UserView]), USER_VIEW)
    deployView(Props(classOf[AccountView]), ACCOUNT_VIEW)
    deployView(Props(classOf[UserOrdersView]), USER_ORDERS_VIEW)
    deployView(Props(classOf[RobotMetricsView]), ROBOT_METRICS_VIEW)
    deployView(Props(new ApiAuthView(apiAuthSecret)), API_AUTH_VIEW)

    // Then deploy routers
    val routers = new LocalRouters(markets)

    // Finally deploy processors
    markets foreach { m =>
      val props = Props(new MarketProcessor(m, routers.accountProcessor.path, routers.marketUpdateProcessor.path))
      deployProcessor(props, MARKET_PROCESSOR(m))
    }

    deployProcessor(Props(new UserProcessor(routers.mailer, userManagerSecret)), USER_PROCESSOR)
    deployProcessor(Props(new AccountProcessor(routers.marketProcessors)), ACCOUNT_PROCESSOR)
    deployProcessor(Props(new MarketUpdateProcessor()), MARKET_UPDATE_PROCESSOR)
    deployProcessor(Props(new ApiAuthProcessor(apiAuthSecret)), API_AUTH_PROCESSOR)

    // Deploy monitor at last
    deployMonitor(routers)
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
    println("Started HTTP server: http://" + hostname + ":" + port)
  }
}