package com.coinport.coinex

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig
import scala.concurrent.duration._
import akka.contrib.pattern.ClusterSingletonManager
import Domain._

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.load("application"))

  val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)

  // cluster aware routers
  val accountProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "accountProcessorRouter")
  val accountViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "accountViewRouter")
  val marketProcessorRouter = system.actorOf(FromConfig.props(Props.empty), name = "marketProcessorRouter")
  val marketViewRouter = system.actorOf(FromConfig.props(Props.empty), name = "marketViewRouter")

  // actors

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new AccountProcessor(marketProcessorRouter.path)),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("account_processor")),
    name = "accountProcessor")

  if (cluster.selfRoles.contains("account_view")) {
    system.actorOf(Props(classOf[AccountView]), "accountView")
  }

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(new MarketProcessor(Market("BTC", "RMB"), accountProcessorRouter.path)),
    singletonName = "singleton",
    terminationMessage = PoisonPill,
    role = Some("market_processor")),
    name = "marketProcessor")

  if (cluster.selfRoles.contains("market_view")) {
    system.actorOf(Props(classOf[MarketView]), "marketView")
  }

  Thread.sleep(10000)
  import system.dispatcher
  val test = system.actorOf(Props(new Test(accountProcessorRouter, marketProcessorRouter)))

  class Test(ap: ActorRef, mp: ActorRef) extends Actor {
    override def preStart = {
      // context.system.scheduler.scheduleOnce(1 second, 10 second) {
      context.system.scheduler.scheduleOnce(1 second) {
        if (args.length > 1) {
          ap ! DoDeposit(Deposit(123L, "RMB", 1000))
          ap ! DoDeposit(Deposit(456L, "BTC", 1))
          ap ! DebugDump

          ap ! SubmitOrder(Order(4, 123L, Market("BTC", "RMB"), 666, None))
          ap ! SubmitOrder(Order(6, 456L, Market("RMB", "BTC"), 1, Some(333.0)))
        }
        ap ! DebugDump
        mp ! DebugDump
      }

      context.system.scheduler.schedule(10 seconds, 10 second) {
        ap ! DebugDump
        mp ! DebugDump
      }
    }

    def receive = {
      case a: Deposit => println("~~~~~~~~~~~~~~~~~~~~ deposit processed: " + a)
    }
  }
}