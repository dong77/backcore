package com.coinport.coinex

import scala.sys.process.Process

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import com.coinport.coinex.common.Constants
import com.coinport.coinex.data.Currency.Btc
import com.coinport.coinex.data.Currency.Rmb
import com.coinport.coinex.data.Implicits.currency2Rich
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoURI
import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.testkit.ImplicitSender
import akka.testkit.TestKit

final class Environment {
  val config = ConfigFactory.parseString("akka.cluster.roles=[" + Constants.ALL_ROLES + "]")
    .withFallback(ConfigFactory.load("integration.conf"))
  val hostname = "127.0.0.1"
  val markets = Seq(Btc ~> Rmb)

  implicit val system = ActorSystem("coinex", config)
  implicit val cluster = Cluster(system)

  val proc = Process("sh", Seq("-c", "\"mongod\"", "&")).run
  val deployer = new Deployer(config, hostname, markets)

  val mongo = MongoConnection(MongoURI("mongodb://localhost:27017"))
  mongo.dbNames foreach (mongo.dropDatabase)
  mongo.close

  val routers = deployer.deploy()

  Thread.sleep(5000)
  val client = system.actorOf(Props(new Coinex(routers)))
}

abstract class IntegrationTest(val env: Environment) extends TestKit(env.system)
    with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(new Environment)

  override def afterAll {
    try {
      system.shutdown()
      env.deployer.shutdown()
      env.proc.destroy
    } catch {
      case e: Throwable =>
    }
  }
}