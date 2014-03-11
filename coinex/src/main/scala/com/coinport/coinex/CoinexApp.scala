/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.cluster._
import akka.cluster.routing._
import akka.routing._
import akka.contrib.pattern._
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import com.coinport.coinex.accounts._
import com.coinport.coinex.markets._

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0))
    .withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)
  val markets = Seq(BTC ~> RMB)

  // ------------------------------------------------------------------------------------------------
  // Cluster-aware Routers Deployment
  val routers = new LocalRouters(markets)

  // ------------------------------------------------------------------------------------------------
  // Processors and Views Deployment
  // Account Processor (path: /user/ap/singleton)
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new AccountProcessor(routers.marketProcessors)),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("ap")),
    name = "ap")

  // Account View (path: /user/av)
  if (cluster.selfRoles.contains("av")) {
    system.actorOf(Props(classOf[AccountView]), "av")
  }

  // Market Processors and Views
  markets foreach { market =>
    // path: /user/mp_btc_rmb/singleton
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(new MarketProcessor(market, routers.accountProcessor.path)),
      singletonName = "singleton",
      terminationMessage = PoisonPill,
      role = Some("mp_" + market)),
      name = "mp_" + market)

    // Market views (path: /user/mv_btc_rmb)
    if (cluster.selfRoles.contains("mv_" + market)) {
      system.actorOf(Props(new MarketView(market)), "mv_" + market)
    }
  }

  val sleep = config.getInt("coinex.app-snap-seconds")
  println("sleeping for " + sleep + " seconds...")
  Thread.sleep(sleep * 1000) // give time for event replay

  println("============= Akka Node Ready =============\n\n")
  class Test extends Actor {
    def receive = {
      case "go" =>
        (1 to 100) foreach { i =>
          routers.accountProcessor ! DoDepositCash(1L, RMB, i.toLong)
          Thread.sleep(2000)
        }

    }
  }

  system.actorOf(Props(new Test)) ! "go"
}