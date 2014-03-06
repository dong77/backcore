package com.coinport.coinex

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig
import scala.concurrent.duration._
import akka.contrib.pattern.ClusterSingletonManager
import com.coinport.coinex.rest.DemoServiceActor
import akka.io.IO
import spray.can.Http
import com.coinport.coinex.domain._
import akka.cluster.routing._
import akka.routing.ConsistentHashingGroup

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)

  // cluster aware routers
  val accountProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "ap_router")
  val accountViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "av_router")

  val markets = Seq(BTC ~> RMB)

  val marketProcessors = Map(
    markets map { market =>
      market -> system.actorOf(
        ClusterRouterGroup(
          ConsistentHashingGroup(Nil),
          ClusterRouterGroupSettings(
            totalInstances = 1,
            routeesPaths = List("/user/mp_" + market + "/singleton"),
            allowLocalRoutees = true,
            useRole = None)).props, market + "mp_" + market + "_router")
    }: _*)

  val marketViews = markets map { market =>
    market -> system.actorOf(
      ClusterRouterGroup(
        ConsistentHashingGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = 3,
          routeesPaths = List("/user/mv_" + market),
          allowLocalRoutees = true,
          useRole = None)).props, "mv_" + market + "_router")
  }

  //deploy account processor
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new AccountProcessor(marketProcessors)),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("ap")),
    name = "ap")

  // deploy account view
  if (cluster.selfRoles.contains("av")) {
    system.actorOf(Props(classOf[AccountView]), "av")
  }

  // deploy market processors and views
  markets foreach { market =>
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(new MarketProcessor(market, accountProcessorRouter.path)),
      singletonName = "singleton",
      terminationMessage = PoisonPill,
      role = Some("mp_" + market)),
      name = "mp_" + market)

    if (cluster.selfRoles.contains("mv_" + market)) {
      system.actorOf(Props(new MarketView(market)), "mv_" + market)
    }
  }

  Thread.sleep(5000)

  val service = system.actorOf(Props[DemoServiceActor], "rest")
  IO(Http) ! Http.Bind(service, "localhost", port = config.getInt("coinex.rest-http-port"))
}