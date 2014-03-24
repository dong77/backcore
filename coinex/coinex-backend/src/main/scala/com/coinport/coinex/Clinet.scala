/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex

import akka.actor.{ Props, ActorSystem }
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory

import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.EmailType._
import com.coinport.coinex.data.Implicits._

object Client {
  private val config = ConfigFactory.load("client.conf")
  private implicit val system = ActorSystem("coinex", config)
  private implicit val cluster = Cluster(system)
  private val markets = Seq(Btc ~> Rmb)
  private val routers = new LocalRouters(markets)

  val backend = system.actorOf(Props(new Coinex(routers)), name = "backend")
}
