package com.coinport.coinex

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig
import scala.concurrent.duration._
import akka.contrib.pattern.ClusterSingletonManager
// import com.coinport.coinex.rest.DemoServiceActor
import akka.io.IO
// import spray.can.Http
import com.coinport.coinex.domain._

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)

  // cluster aware routers
  val accountProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "accountProcessorRouter")
  val accountViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "accountViewRouter")
  val marketProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "marketProcessorRouter")
  val marketViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "marketViewRouter")

  // processors
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new AccountProcessor()),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("account_processor")),
    name = "accountProcessor")

  val market = BTC ~> RMB
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new MarketProcessor(market, accountProcessorRouter.path)),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("market_processor")),
    name = "marketProcessor")

  // views
  if (cluster.selfRoles.contains("account_view")) {
    system.actorOf(Props(classOf[AccountView]), "accountView")
  }

  if (cluster.selfRoles.contains("market_view")) {
    system.actorOf(Props(new MarketView(market)), "marketView")
  }

  Thread.sleep(5000)

  // val service = system.actorOf(Props[DemoServiceActor], "rest")
  // IO(Http) ! Http.Bind(service, "localhost", port = config.getInt("coinex.rest-http-port"))
}
