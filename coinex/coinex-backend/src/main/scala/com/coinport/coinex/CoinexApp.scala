/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.pattern.ask
import akka.cluster.Cluster
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import Implicits._
import Currency._
import akka.persistence._
import scala.concurrent.duration._
import akka.util.Timeout
import java.net.InetAddress
import com.typesafe.config.Config
import com.coinport.coinex.common.Constants

object CoinexApp extends App {
  val markets = Seq(Btc ~> Rmb, Ltc ~> Rmb)
  val allRoles = (ConstantRole.values.map(_.<<) ++ MarketRole.values.map { v => markets.map { m => v << m } }.flatten)

  if (args.length < 2 || args.length > 4) {
    val message = """please supply 1 to 4 parameters:
        required args(0): port - supply 0 to select a port randomly
        required args(1): seeds - seed note seperated by comma, i.e, "127.0.0.1:25551,127.0.0.1:25552"
        optioanl args(2): roles - "*" for all roles, "" for empty node, and "a,b,c" for 3 roles
        optioanl args(3): hostname - self hostname

        available roles:%s
      """.format(allRoles.mkString("\n\t\t", "\n\t\t", "\n\t\t"))
    println(message)
    System.exit(1)
  }

  val seeds = args(1).split(",").map(_.stripMargin).filter(_.nonEmpty).map("\"akka.tcp://coinex@" + _ + "\"").mkString(",")

  val roles =
    if (args.length < 3) ""
    else if (args(2) == "*") allRoles.mkString(",")
    else args(2).split(",").map(_.stripMargin).filter(_.nonEmpty).map("\"" + _ + "\"").mkString(",")

  val hostname =
    if (args.length < 4) InetAddress.getLocalHost.getHostAddress
    else args(3)

  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0))
    .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + hostname))
    .withFallback(ConfigFactory.parseString("akka.cluster.roles=[" + roles + "]"))
    .withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=[" + seeds + "]"))
    .withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("coinex", config)
  implicit val cluster = Cluster(system)

  val routers = new Deployer(config, hostname, markets).deploy()

  Thread.sleep(5000)
  val summary = "============= Akka Node Ready =============\n" +
    "with hostname: " + hostname + "\n" +
    "with seeds: " + seeds + "\n" +
    "with roles: \n" + roles + "\n"

  println(summary)
  val coinport = """
                                            _
             (_)                           | |
   ___  ___   _  _ __   _ __    ___   _ __ | |_ __  __
  / __|/ _ \ | || '_ \ | '_ \  / _ \ | '__|| __|\ \/ /
 | (__| (_) || || | | || |_) || (_) || |   | |_  >  <
  \___|\___/ |_||_| |_|| .__/  \___/ |_|    \__|/_/\_\
                       | |
                       |_|
"""
  println(coinport)
}

