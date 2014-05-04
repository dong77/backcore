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
import akka.actor._
import akka.util.Timeout
import com.coinport.coinex.robot.sample.StopOrderRobot
import scala.concurrent.ExecutionContext.Implicits.global

object Client {
  implicit val timeout = Timeout(10 seconds)

  private val config = ConfigFactory.load("client.conf")
  private implicit val system = ActorSystem("coinex", config)
  private implicit val cluster = Cluster(system)
  private val markets = Seq(Btc ~> Cny)
  private val routers = new LocalRouters(markets)

  val backend = system.actorOf(Props(new Coinex(routers)), name = "backend")
  println("Example: Client.backend ! SomeMessage()")

  System.setProperty("akka.config", "client.conf")

  val userMap = Map(
    "wd" -> -245561917658914311L,
    "c" -> -6771488127296557565L,
    "lcm" -> 877800447188483646L,
    "cx" -> 91990591289398244L,
    "lwc" -> 5742988204510593740L,
    "xl" -> 11190591289398244L,
    "kl" -> 22290591289398244L
  )

  userMap.map(kv => registerUser(kv._2, kv._1 + "@163.com", kv._1))
  var basicRisk = 10.0
  val risk = userMap.map(kv => { basicRisk += 10.0; (kv._2 -> basicRisk) })

  var did = -1
  def addGofvRobots() {
    userMap foreach { kv =>
      AccountService.deposit(kv._2, Btc, 10000 * 1000)
      AccountService.deposit(kv._2, Cny, 1000000 * 100)

      val dna = Map(
        "START" -> """
        (robot -> "LOOP", None)
        """,

        "LOOP" -> """
        import scala.util.Random
        import com.coinport.coinex.data._
        import com.coinport.coinex.data.Currency._

        println("*"*40)
        var counter = robot.getPayload[Int]("COUNTER").get
        println(counter)
        println("*"*40)
        val r = robot.setPayload("COUNTER", Some(counter+1))
        val btcSide = MarketSide(Btc, Cny)
        val rmbSide = MarketSide(Cny, Btc)
        val side = List(btcSide, rmbSide)(Random.nextInt(2))
        val price = metrics match {
          case None => if (side == btcSide) 3000.0 else 1 / 3000.0
          case Some(m) => m.metricsByMarket.get(side) match {
            case Some(mbm) => mbm.price
            case _ => if (side == btcSide) 3000.0 else 1 / 3000.0
          }
        }

        val range = %f - Random.nextInt(100)
        var orderPrice = price * (1 + range / 100.0)
        var quantity = 10 * (1 + range / 100.0)
        if (side == rmbSide) quantity /= orderPrice
        if (side == btcSide) {
          quantity = Random.nextDouble() + Random.nextInt(1000)
          orderPrice = 250 + Random.nextInt(100)
        } else {
          quantity = 100000 + Random.nextInt(900000)
          orderPrice = 1.0/(250 + Random.nextInt(100))
        }
        val action = Some(DoSubmitOrder(side,
          Order(robot.userId, 0, quantity.toLong, price = Some(orderPrice), robotId = Some(robot.robotId))))
        (r -> "LOOP", action)
        """.format(risk(kv._2))
      )

      val order = Order(1L, 2L, 10L, inAmount = 30L)
      val payload: Map[String, Option[Any]] =
        Map("SP" -> Some(120L), "ORDER" -> Some(order), "COUNTER" -> Some(101), "SIDE" -> Some("tttt"))
      Client.backend ? DoAddRobotDNA(dna) map {
        case AddRobotDNAFailed(ErrorCode.RobotDnaExist, existingDNAId) =>
          val payload = Map("SP" -> Some(120L), "ORDER" -> Some(order), "COUNTER" -> Some(101), "SIDE" -> Some("tttt"))
          val robot = Robot(kv._2, kv._2, kv._2, dnaId = existingDNAId, payloads = payload)
          println("exist robot dna >>>> id: " + existingDNAId)
          Client.backend ! DoSubmitRobot(robot)
        case mid =>
          val robot = Robot(kv._2, kv._2, kv._2, dnaId = mid.asInstanceOf[Long], payloads = payload)
          println("generate robot >>>> id: " + robot.robotId)
          Client.backend ! DoSubmitRobot(robot)
      }
    }
  }

  def performanceTest() {

    import scala.util.Random
    import com.coinport.coinex.data._
    import com.coinport.coinex.data.Currency._
    userMap foreach { kv =>
      AccountService.deposit(kv._2, Btc, 1000000 * 1000)
      AccountService.deposit(kv._2, Cny, 1000000000 * 100)
    }

    Thread.sleep(4000)

    val cycle = 10000
    var result = 0
    var startTime: Long = System.currentTimeMillis
    1 to cycle foreach { i =>
      //      Thread.sleep(100)
      val btcSide = MarketSide(Btc, Cny)
      val rmbSide = MarketSide(Cny, Btc)
      val side = List(btcSide, rmbSide)(Random.nextInt(2))
      var quantity = 0d
      var orderPrice = 0d
      if (side == btcSide) {
        quantity = Random.nextDouble() + Random.nextInt(1000)
        orderPrice = 250 + Random.nextInt(100)
      } else {
        quantity = 100000 + Random.nextInt(900000)
        orderPrice = 1.0 / (250 + Random.nextInt(100))
      }
      val userId = userMap.values.toList(Random.nextInt(userMap.size))
      val f = Client.backend ? DoSubmitOrder(side, Order(userId, 0, quantity.toLong, price = Some(orderPrice), robotId = Some(userId)))

      if (i == 10) {
        startTime = System.currentTimeMillis
        println("======================startTime : " + startTime)
      }

      f onSuccess {

        case m => {
          result += 1
          if (result % 1000 == 0) println("*")
          if (result == 9900) {
            val endTime = System.currentTimeMillis
            println("endTime : " + endTime)
            println("execute Time : " + (endTime - startTime))
            println("qps : " + cycle * 1000.0 / (endTime - startTime))
          }
          //          println(result)
          //          println(m)
        }
      }
      f onFailure {
        case m => {
          result += 1
          if (result % 1000 == 0) println("*")
          if (result == 9900) {
            val endTime = System.currentTimeMillis
            println("endTime : " + endTime)
            println("execute Time : " + (endTime - startTime))
            println("qps : " + cycle * 1000.0 / (endTime - startTime))
          }
          //          println(result)
          //          println(m)
        }
      }
      //      Client.backend ? DoSubmitOrder(side,
      //        Order(userId, 0, quantity.toLong, price = Some(orderPrice), robotId = Some(userId))) map {
      //        case m => {
      //          result += 1
      //          println("*" * 20 + "  " + result)
      //          println(m)
      //          if (result % 1000 == 0) println("*")
      //          if (result == 10000) {
      //            val endTime = System.currentTimeMillis
      //            println("endTime : " + endTime)
      //            println("execute Time : " + (endTime - startTime))
      //            println("qps : " + cycle / (endTime - startTime))
      //          }
      //        }
      //      }
    }
  }

  def removeGofvRobots() {
    userMap foreach { kv =>
      Client.backend ! DoCancelRobot(kv._2)
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
    println("add user >>>> " + mail)
  }

  def deposit(uid: Long, currency: Currency, amount: Double) =
    AccountService.deposit(1001, Currency.Cny, 10000.0)

  def createABCode(wUserId: Long, amount: Long, dUserId: Long) {
    Client.backend ? DoRequestGenerateABCode(wUserId, amount, None, None) map {
      case RequestGenerateABCodeFailed(ErrorCode.InsufficientFund) => println("create ab code failed")
      case RequestGenerateABCodeSucceeded(a, b) => {
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

  def registerTestUsers {
    userMap.map(kv => registerUser(kv._2, kv._1 + "@coinport.com", kv._1))
  }

  def main(args: Array[String]) {

    args(0) match {
      case "add" => addGofvRobots
      case "rm" => removeGofvRobots
    }
  }
}
