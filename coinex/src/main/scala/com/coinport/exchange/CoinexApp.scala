package com.coinport.exchange

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig

import com.coinport.exchange.domain.Commands._
import com.coinport.exchange.domain.Transfer
import actors._
import processors._
import views._

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.parseString("akka.cluster.roles = [" + args(1) + "]")).
    withFallback(ConfigFactory.load("coinex"))

  val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)

  val balanceProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceProcessorRouter")
  val balanceViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceViewRouter")
  val balanceAdminViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceAdminViewRouter")

  val transferProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferProcessorRouter")
  val transferViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferViewRouter")
  val transferAdminViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferAdminViewRouter")

  val markethubProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubProcessorRouter")
  val markethubViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubViewRouter")
  val markethubAdminViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubAdminViewRouter")

  val routers = new LocalRouters(
    balanceProcessorRouter,
    balanceViewRouter,
    balanceAdminViewRouter,
    transferProcessorRouter,
    transferViewRouter,
    transferAdminViewRouter,
    markethubProcessorRouter,
    markethubViewRouter,
    markethubAdminViewRouter)

  var actors = Set.empty[ActorRef]

  actors += system.actorOf(Props(new RoleSingletonManager("bp", Props(new BalanceProcessor(routers)))), "balanceProcessor")
  actors += system.actorOf(Props(new RoleSingletonManager("tp", Props(new TransferProcessor(routers)))), "transferProcessor")
  actors += system.actorOf(Props(new RoleSingletonManager("hp", Props(new MarkethubProcessor(routers)))), "markethubProcessor")

  if (cluster.selfRoles.contains("bv" /* balance view */ )) {
    actors += system.actorOf(Props(classOf[BalanceView]), "balanceView")
    actors += system.actorOf(Props(classOf[BalanceAdminView]), "adminBalanceView")
  }

  if (cluster.selfRoles.contains("tv" /* transfer view */ )) {
    actors += system.actorOf(Props(classOf[TransferView]), "transferView")
    actors += system.actorOf(Props(classOf[TransferAdminView]), "adminTransferView")
  }

  if (cluster.selfRoles.contains("hv" /* markethub view */ )) {
    actors += system.actorOf(Props(classOf[MarkethubView]), "markethubView")
    actors += system.actorOf(Props(classOf[MarkethubAdminView]), "markethubView")
  }

  if (cluster.selfRoles.contains("f")) {
    actors += system.actorOf(Props(new Frontdesk(routers)), "frontdesk")
  }

  println("=== local actors: " + actors.mkString("\n"))
  println("=== cluster routers: " + routers)

  Thread.sleep(5000)

  println("=======================")
  (1 to 5) foreach { i =>
    transferProcessorRouter ! DoCreatePendingDeposit(Transfer(-1, 2L, i * 10))
    transferProcessorRouter ! DoConfirmDeposit(i)
  }
}