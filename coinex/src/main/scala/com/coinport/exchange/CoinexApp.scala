package com.coinport.exchange

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig
import actors._
import processors._
import views._
import akka.contrib.pattern.ClusterSingletonManager

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.parseString("akka.cluster.roles = [" + args(1) + "]")).
    withFallback(ConfigFactory.load("coinex"))

  val system = ActorSystem("ClusterSystem", config)
  val cluster = Cluster(system)
  var actors = Set.empty[ActorRef]
  // TODO: kill all other actors if this JVM hosts even one processor.

  // balance processor
  actors += system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(classOf[BalanceProcessor]),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("bp")),
    name = "balanceProcessor")

  // transfer processor
  actors += system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(classOf[TransferProcessor]),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("tp")),
    name = "transferProcessor")

  // markethub processor
  actors += system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(classOf[MarkethubProcessor]),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("tp")),
    name = "markethubProcessor")

  if (cluster.selfRoles.contains("bv" /* balance view */ )) {
    actors += system.actorOf(Props(classOf[BalanceView]), "balanceView")
    actors += system.actorOf(Props(classOf[AdminBalanceView]), "adminBalanceView")
  }

  if (cluster.selfRoles.contains("tv" /* transfer view */ )) {
    actors += system.actorOf(Props(classOf[TransferView]), "transferView")
    actors += system.actorOf(Props(classOf[AdminTransferView]), "adminTransferView")
  }

  if (cluster.selfRoles.contains("mhv" /* markethub view */ )) {
    actors += system.actorOf(Props(classOf[MarkethubView]), "markethubView")
  }

  if (cluster.selfRoles.contains("f")) {
    actors += system.actorOf(Props(classOf[Frontdesk]), "frontdesk")
  }

  val balanceProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceProcessorRouter")
  val balanceViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceViewRouter")
  val adminBalanceViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "adminBalanceViewRouter")

  val transferProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferProcessorRouter")
  val transferViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferViewRouter")
  val adminTransferViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "adminTransferViewRouter")

  val markethubProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubProcessorRouter")
  val markethubViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubViewRouter")

  val routers = new LocalRouters(
    balanceProcessorRouter,
    balanceViewRouter,
    adminBalanceViewRouter,
    transferProcessorRouter,
    transferViewRouter,
    adminTransferViewRouter,
    markethubProcessorRouter,
    markethubViewRouter)

  actors foreach { actor =>
    actor ! routers
  }
  println("local actors: " + actors)
  println("cluster routers: " + routers)

  Thread.sleep(5000)
  val delayFactor = cluster.state.members.size + 1
  Thread.sleep(5000 / delayFactor)

  system.actorSelection("/user/frontdesk") ! "Start"
}