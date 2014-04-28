package com.coinport.coinex.api.service

import com.typesafe.config.ConfigFactory
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.{ Coinex, LocalRouters }
import com.coinport.coinex.data.Implicits._
import akka.actor.{ Props, ActorSystem }
import akka.cluster.Cluster

object Akka {
  val defaultAkkaConfig = "akka.conf"
  val akkaConfigProp = System.getProperty("akka.config")
  val akkaConfigResource = if (akkaConfigProp != null) akkaConfigProp else defaultAkkaConfig

  println("=" * 20 + "  Akka config  " + "=" * 20)
  println("  conf/" + akkaConfigResource)
  println("=" * 55)

  val config = ConfigFactory.load(akkaConfigResource)
  implicit val system = ActorSystem("coinex", config)
  implicit val cluster = Cluster(system)

  // TODO: load markets definition from config
  val markets = Seq(Btc ~> Cny)

  val routers = new LocalRouters(markets)
  val backend = system.actorOf(Props(new Coinex(routers)), name = "backend")
}
