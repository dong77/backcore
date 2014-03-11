/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import com.typesafe.config.ConfigFactory

import akka.actor._
import akka.cluster.Cluster
import data._
import com.coinport.coinex.data._
import Implicits._
import Currency._

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0))
    .withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("coinex", config)
  implicit val cluster = Cluster(system)

  val markets = Seq(Btc ~> Rmb)

  val routers = new LocalRouters(markets)
  val deployer = new Deployer(markets)
  deployer.deploy(routers)

  Thread.sleep(5000)
  println("============= Akka Node Ready =============\n\n")

  ////////  TO  BE DELETED //////////////////////////////////////////////////
  if (args.size > 1)
    (1 to 1000) foreach { i =>
      routers.accountView ! "hi " + i
      Thread.sleep(1000)
    }

}