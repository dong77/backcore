package com.coinport.exchange

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._

import roleplay._
import roles._

object ExchangeApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.parseString("akka.cluster.roles = [" + args(1) + "]")).
    withFallback(ConfigFactory.load("application"))

  val system = ActorSystem("ClusterSystem", config)

  val settings = new RolePlaySettings()
    .support("balance", RoleSetting(Props(classOf[BalanceProcessingRoleActor]), Some(Props(classOf[BalanceProcessingRoleLeaderActor]))))
    .support("market_1", RoleSetting(Props(classOf[MarketProcessingRoleActor]), Some(Props(classOf[MarketProcessingRoleLeaderActor]))))

  val clusterListener = system.actorOf(Props(new RolePlay(settings)), "director")

}
