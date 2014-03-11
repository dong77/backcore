/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.pattern.ask
import akka.cluster._
import akka.cluster.routing._
import akka.routing._
import akka.contrib.pattern._
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import com.coinport.coinex.users._
import com.coinport.coinex.accounts._
import com.coinport.coinex.markets._
import akka.util.Timeout
import scala.concurrent.duration._

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

  // Account Processor (path: /user/up/singleton)
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new UserProcessor()),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("up")),
    name = "up")

  // Account View (path: /user/uv)
  if (cluster.selfRoles.contains("uv")) {
    system.actorOf(Props(classOf[UserView]), "uv")
  }

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

  
  ////////  TO  BE DELETED //////////////////////////////////////////////////
  routers.accountProcessor ! Persistent(DoDepositCash(123L, RMB, 10000))
  routers.accountProcessor ! Persistent(DoDepositCash(456, BTC, 2))
  Thread.sleep(2000)
  routers.accountProcessor ! Persistent(DoSubmitOrder(BTC ~> RMB, Order(456, 1, 1, Some(4000))))
  routers.accountProcessor ! Persistent(DoSubmitOrder(RMB ~> BTC, Order(123, 2, 5000, None)))
  import system.dispatcher
  Thread.sleep(5000)
  implicit val timeout = Timeout(5 seconds)
  
  
  routers.accountView ask QueryAccount(123) map {
    x => println(x)
  }

  routers.marketViews(BTC ~> RMB) ask QueryMarket(BTC ~> RMB, 10) map {
    x => println(x)
  }

  Thread.sleep(4000)
  println("------------J")
  routers.accountProcessor ! DebugDump
  routers.accountView ! DebugDump
  routers.marketProcessors(BTC ~> RMB) ! DebugDump
  routers.marketViews(BTC ~> RMB) ! DebugDump

}