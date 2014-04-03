package com.coinport.coinex

import com.typesafe.config.ConfigFactory
import akka.testkit._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.data.Currency._
import scala.sys.process._

import akka.cluster.Cluster
import com.coinport.coinex._
import akka.actor.ActorSystem
import akka.actor.Props

import org.scalatest._

object IntegrationTest extends App { //with TestKitBase with WordSpecLike with Matchers {
  val config = ConfigFactory.load("application.conf")
  val hostname = "127.0.0.1"
  val markets = Seq(Btc ~> Rmb)

  implicit val system = ActorSystem("coinex", config)
  implicit val cluster = Cluster(system)

  val proc = Process("sh", Seq("-c", "\"mongod\"", "&")).run

  new Deployer(config, hostname, markets).deploy()

  Thread.sleep(5000)

  val routers = new LocalRouters(markets)
  val client = system.actorOf(Props(new Coinex(routers)))

  def afterAll = {
    system.shutdown
    proc.destroy
  }
  /*
  "a" must {
    "aaa" in {
      val deposit = Deposit(1, 10000, Rmb, 500000000L, TransferStatus.Pending)
      client ! DoRequestCashDeposit(deposit)
      expectMsg(RequestCashDepositSucceeded(deposit))
    }
  }*/
}

