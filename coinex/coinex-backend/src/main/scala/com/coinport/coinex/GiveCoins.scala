/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex

import scala.concurrent.duration._
import com.coinport.coinex.api.model._
import com.coinport.coinex.api.service._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.EmailType._
import com.coinport.coinex.data.Implicits._
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import com.coinport.coinex.robot.sample.StopOrderRobot
import scala.concurrent.ExecutionContext.Implicits.global

object GiveCoins {

  import scala.concurrent.{ ExecutionContext, Future, Await }

  implicit val timeout = Timeout(1000 seconds)
  val configPath = System.getProperty("akka.config") match {
    case null => "akka.conf"
    case c => c
  }
  private val config = ConfigFactory.load(configPath)
  private implicit val system = ActorSystem("coinex", config)
  private implicit val cluster = Cluster(system)

  private val markets = Seq(Ltc ~> Btc, Doge ~> Btc, Bc ~> Btc, Drk ~> Btc, Vrc ~> Btc, Zet ~> Btc)
  val routers = new LocalRouters(markets)
  val backend = system.actorOf(Props(new Coinex(routers)), name = "backend")

  def main(args: Array[String]) {

    // * DO NOT! * puts valid id to the github. Modify when using it
    val payer = None

    val List(low, high) = args(0).split("-").map(_.toLong).toList
    val currency = Currency.valueOf(args(1))
    val amount = args(2).toDouble.internalValue(currency.get)
    val reason = args(3)

    (low to high) foreach { payee =>
      val request = DoRequestPayment(Payment(0, payer.get, payee, currency.get, amount, reason = Some(reason)))
      val response = Await.result(backend ? request, 5.second)
      println("the result for request: " + request + " is: " + response)
    }
  }
}
