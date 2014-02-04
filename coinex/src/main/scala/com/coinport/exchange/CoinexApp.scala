package com.coinport.exchange

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig
import actors._
import processors._
import views._

object NewApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.parseString("akka.cluster.roles = [" + args(1) + "]")).
    withFallback(ConfigFactory.load("coinex"))

  val system = ActorSystem("ClusterSystem", config)
  val cluster = Cluster(system)
  var actors = Set.empty[ActorRef]

  if (cluster.getSelfRoles.contains("bp" /* balance processor */ )) {
    //TODO: make singleton
    actors += system.actorOf(Props(classOf[BalanceProcessor]), "balanceProcessor")
  }
  // TODO: check if `bp` is on this node
  if (cluster.getSelfRoles.contains("bv" /* balance view */ )) {
    actors += system.actorOf(Props(classOf[BalanceView]), "balanceView")
    actors += system.actorOf(Props(classOf[AdminBalanceView]), "adminBalanceView")
  }

  if (cluster.getSelfRoles.contains("tp" /* transfer processor */ )) {
    //TODO: make singleton
    actors += system.actorOf(Props(classOf[TransferProcessor]), "transferProcessor")
  }

  if (cluster.getSelfRoles.contains("tv" /* transfer view */ )) {
    actors += system.actorOf(Props(classOf[TransferView]), "transferView")
    actors += system.actorOf(Props(classOf[AdminTransferView]), "adminTransferView")
  }

  if (cluster.getSelfRoles.contains("mhp" /* markethub processor */ )) {
    //TODO: make singleton
    actors += system.actorOf(Props(classOf[MarkethubProcessor]), "markethubProcessor")
  }

  if (cluster.getSelfRoles.contains("mhv" /* markethub view */ )) {
    actors += system.actorOf(Props(classOf[MarkethubView]), "markethubView")
  }

  if (cluster.getSelfRoles.contains("f")) {
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

  println("local actors: " + actors)
  println("cluster routers: " + routers)
  actors foreach { actor =>

    actor ! routers
  }
}