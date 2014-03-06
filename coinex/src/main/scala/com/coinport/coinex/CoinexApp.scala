package com.coinport.coinex

import com.typesafe.config.ConfigFactory
import com.coinport.coinex.domain._
import akka.actor._
import akka.cluster._
import akka.cluster.routing._
import akka.routing._
import akka.contrib.pattern._
import akka.persistence.Persistent

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0))
    .withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)

  // ------------------------------------------------------------------------------------------------
  // Cluster-aware Routers Deployment
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

  // ------------------------------------------------------------------------------------------------
  // Processors and Views Deployment

  // Account Processor
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new AccountProcessor(marketProcessors)),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("ap")),
    name = "ap")

  // Account View
  if (cluster.selfRoles.contains("av")) {
    system.actorOf(Props(classOf[AccountView]), "av")
  }

  // Market Processors and Views
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

  Thread.sleep(10000) // give time for event replay

  // ------------------------------------------------------------------------------------------------
  // Front-end Deployment

  println("============= Akka Node Ready =============\n\n")
}