package com.coinport.coinex.integration

import org.scalatest._
import com.coinport.coinex._
import com.coinport.coinex.common._
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits._
import com.typesafe.config.ConfigFactory
import akka.cluster.Cluster
import akka.testkit._
import akka.actor.ActorSystem
import akka.actor.Props

final class Environment extends Object with EmbeddedMongoSupport {
  val config = ConfigFactory.parseString("akka.cluster.roles=[" + Constants.ALL_ROLES + "]")
    .withFallback(ConfigFactory.load("integration.conf"))
  val markets = Seq(Btc ~> Rmb, Btc ~> Ltc)

  embeddedMongoStartup()
  implicit val system = ActorSystem("coinex", config)
  implicit val cluster = Cluster(system)

  val deployer = new Deployer(config, "localhost", markets)
  val routers = deployer.deploy()

  Thread.sleep(2000)
  val client = system.actorOf(Props(new Coinex(routers)))
}

abstract class IntegrationSpec(val env: Environment) extends TestKit(env.system)
    with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(new Environment)

  override def afterAll {
    try {
      system.shutdown()
      system.awaitTermination()
      env.deployer.shutdown()
      env.embeddedMongoShutdown()
    } catch {
      case e: Throwable =>
    }
  }
}