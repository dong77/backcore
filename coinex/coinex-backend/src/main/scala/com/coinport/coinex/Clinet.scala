/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex

import scala.concurrent.duration._
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
import akka.util.Timeout
import com.coinport.coinex.robot.sample.StopOrderRobot

object Client {
  implicit val timeout = Timeout(10 seconds)

  private val config = ConfigFactory.load("client.conf")
  private implicit val system = ActorSystem("coinex", config)
  private implicit val cluster = Cluster(system)
  private val markets = Seq(Btc ~> Rmb)
  private val routers = new LocalRouters(markets)

  val backend = system.actorOf(Props(new Coinex(routers)), name = "backend")
  println("Example: Client.backend ! SomeMessage()")

  System.setProperty("akka.config", "client.conf")

  implicit val ec = AccountService.system.dispatcher

  val wd = -245561917658914311L
  val c = -6771488127296557565L
  val lcm = 877800447188483646L
  val cx = 91990591289398244L
  val lwc = 5742988204510593740L

  val players = List(wd, c, lcm, cx, lwc)
  val risk = Map(wd -> 10.0, c -> 30.0, lcm -> 50.0, cx -> 70.0, lwc -> 90.0)

  var did = -1
  def addGofvRobots() {
    players foreach { uid =>
      AccountService.deposit(uid, Btc, 10000 * 1000)
      AccountService.deposit(uid, Rmb, 1000000 * 100)

      val dna = Map(
        "START" -> """
        (robot -> "LOOP", None)
        """,

        "LOOP" -> """
        import scala.util.Random
        val btcSide = MarketSide(Btc, Rmb)
        val rmbSide = MarketSide(Rmb, Btc)
        val side = List(btcSide, rmbSide)(Random.nextInt(2))
        val price = metrics match {
          case None => if (side == btcSide) 3000.0 else 1 / 3000.0
          case Some(m) => m.metricsByMarket.get(side) match {
            case Some(mbm) => mbm.price
            case _ => if (side == btcSide) 3000.0 else 1 / 3000.0
          }
        }

        val range = %f - Random.nextInt(100)
        val orderPrice = price * (1 + range / 100.0)
        var quantity = 10 * (1 + range / 100.0)
        if (side == rmbSide) quantity /= orderPrice
        val action = Some(DoSubmitOrder(side,
          Order(robot.userId, 0, quantity.toLong, price = Some(orderPrice), robotId = Some(robot.robotId))))

        (robot -> "LOOP", action)
        """.format(risk(uid))
      )
      Client.backend ? DoAddRobotDNA(dna) map {
        case AddRobotDNAFailed(ErrorCode.RobotDnaExist, existingDNAId) =>
          val robot = Robot(uid, uid, uid, Map.empty[String, Option[Any]], "START", existingDNAId)
          println("exist robot dna >>>> id: " + existingDNAId)
          Client.backend ! DoSubmitRobot(robot)
        case mid =>
          val robot = Robot(uid, uid, uid, Map.empty[String, Option[Any]], "START", mid.asInstanceOf[Long])
          println("generate robot >>>> id: " + robot.robotId)
          Client.backend ! DoSubmitRobot(robot)
      }
    }
  }

  def removeGofvRobots() {
    players foreach { uid =>
      Client.backend ! DoCancelRobot(uid)
    }
  }

  def registerUser(uid: Long, mail: String, pwd: String) {
    Client.backend ! DoRegisterUser(
      UserProfile(
        id = uid,
        email = mail,
        emailVerified = false,
        mobileVerified = false,
        status = UserStatus.Normal),
      pwd)
  }

  def deposit(uid: Long, currency: Currency, amount: Double) =
    AccountService.deposit(1001, Currency.Rmb, 10000.0)

  def createABCode(wUserId: Long, amount: Long, dUserId: Long) {
    Client.backend ? DoRequestRCWithdrawal(wUserId, amount, None, None) map {
      case RequestRCWithdrawalFailed(ErrorCode.InsufficientFund) => println("create ab code failed")
      case RequestRCWithdrawalSucceeded(a, b) => {
        println("a code: " + a + " b code: " + b)
        Client.backend ? DoRequestACodeQuery(dUserId, a) map {
          case RequestACodeQuerySucceeded(x, y, z) => println(x, y, z)
        }
      }
      case el => println(el)
    }
  }

  def verifyAcode(userId: Long, codeA: String) {
    Client.backend ? DoRequestACodeQuery(userId, codeA) map {
      case RequestACodeQuerySucceeded(x, y, z) => println(x, y, z)
      case RequestACodeQueryFailed(ErrorCode.LockedACode) => println("locked")
    }
  }

  def recharge(userId: Long, codeB: String) {
    Client.backend ? DoRequestBCodeRecharge(userId, codeB) map {
      case m: RequestBCodeRechargeFailed => println(m)
      case RequestBCodeRechargeSucceeded(x, y, z) => println(x, y, z)
    }
  }

  def comfirm(userId: Long, codeB: String, amount: Long) {
    Client.backend ? DoRequestConfirmRC(userId, codeB, amount) map {
      case RequestConfirmRCSucceeded(x, y, z) => println(x, y, z)
      case m => println(m)
    }
  }

  def queryRCDepositRecord(userId: Long) {
    Client.backend ? QueryRCDepositRecord(userId) map {
      case m => println(m)
    }
  }

  def queryRCWithdrawalRecord(userId: Long) {
    Client.backend ? QueryRCWithdrawalRecord(userId) map {
      case m => println(m)
    }
  }
}
