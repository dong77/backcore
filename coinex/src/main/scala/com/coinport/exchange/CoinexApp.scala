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

/**
 * To start a node, run `./activator 'run 2553  "role1,role2,role3"'`.
 * 2553 is the port this node uses, role1, role2, and role3, etc are roles assigned to this node.
 * 
 * When running multiple node, these node will join each other into a cluster named 'coinex',
 * but all nodes except these 'seed nodes' should be configured correctly so that they can find at
 * least one seed node. Seed nodes are configured inside the src/main/resources/coinex.conf.
 * 
 * Default seed node in the config is:
 *   -   	"akka.tcp://coinex@127.0.0.1:2551"
 *   
 * So you should start this seed node first, if it is down, no new node can join the cluster. In
 * production, there should be multiple seed nodes.
 * 
 * But when you start a second seed node itself, seed node configuration in the config file must NOT
 * contain the node itself.
 */
object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.parseString("akka.cluster.roles = [" + args(1) + "]")).
    withFallback(ConfigFactory.load("coinex"))

  val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)

  // Below we create cluster-aware routers for 3 processors and 2 views for each processor.
  
  // Cluster-aware routers can forward messages to actors running on remote node, and those actors may or may
  // not be available when these routers are created. 
  // We create these routers first because they will be used by actors created later.
  
  // Processors are event-sourcing processors, they persist messages into journal and rebuild internal
  // memory state by re-processing all events. Snapshot can also be made to make recovery faster.
  
  // Views are used by the command-query pattern's query part. In this demo, they are declared but not used at all.
  val balanceProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceProcessorRouter")
  val balanceViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceViewRouter")
  val balanceAdminViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "balanceAdminViewRouter")

  val transferProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferProcessorRouter")
  val transferViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferViewRouter")
  val transferAdminViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "transferAdminViewRouter")

  val markethubProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubProcessorRouter")
  val markethubViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubViewRouter")
  val markethubAdminViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "markethubAdminViewRouter")

  // We collect all routers into one case class.
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

  // Hold all actors (except routers) created on this node.
  var actors = Set.empty[ActorRef]

  // We create singleton managers for all three processors. Node that the singleton actor may or may not be created.
  // The balanceProcessor singleton manager will have this path:
  // 	`/user/balanceProcessor`, 
  // If this node is not the role leader, the singleton will be on another node, otherwise it will have this path:
  // 	`/user/balanceProcessor/singleton`
  // Sending messages to the manager won't work, you have to send messages to the singleton itself. The way we send
  // messages to singletons is using cluster-aware routers created previously.
  actors += system.actorOf(Props(new RoleSingletonManager("bp", Props(new BalanceProcessor(routers)))), "balanceProcessor")
  actors += system.actorOf(Props(new RoleSingletonManager("tp", Props(new TransferProcessor(routers)))), "transferProcessor")
  actors += system.actorOf(Props(new RoleSingletonManager("hp", Props(new MarkethubProcessor(routers)))), "markethubProcessor")

  // The following code creates local actors based on this node's role assignment.
  // Sending messages to views is also through cluster-aware routers.
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

  println("=== locally deployed actors: " + actors.mkString("\n", "\n\t","\n"))

  Thread.sleep(5000)

  // Here we create 5 pending deposits then confirm those with id in [1-5]
  // Running this app multiple times will have different effects as all messages are saved into disk
  // so we have a history, to clean the history, you have to drop the `store` collection in mongodb.
  
  (1 to 5) foreach { i =>
    transferProcessorRouter ! DoCreatePendingDeposit(Transfer(-1, 2L, i * 10)) // Trnasfer id is determined by processor.
    transferProcessorRouter ! DoConfirmDeposit(i)
  }
}