package com.coinport.coinex.robot.exchange

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal
import scala.util.Random
import scala.util.parsing.json.JSON.parseFull

import com.coinport.coinex.api.model.ApiMarketDepth
import com.coinport.coinex.api.model.ApiUserAccount
import com.coinport.coinex.api.model.Operations
import com.coinport.coinex.api.model.User
import com.coinport.coinex.api.model.UserOrder
import com.coinport.coinex.api.service.AccountService
import com.coinport.coinex.api.service.MarketService
import com.coinport.coinex.api.service.UserService
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits.currency2Rich
import com.coinport.coinex.data.MarketSide

import dispatch.Http
import dispatch.as
import dispatch.enrichFuture
import dispatch.implyRequestHandlerTuple
import dispatch.url

class AutoExchangeRobotExecutor(marketUrlMap: Map[MarketSide, String], marketUpdateInterval: Long, adjustUserId: Long = 1000000000L) {

  case class User(email: String, exchangeFrequency: Int = 900, takerPercentage: Int = 90, riskPercentage: Int = 10, buyPercentage: Int = 50)
  case class DepthElem(price: Double, quantity: Double)

  var depthBuy: Map[MarketSide, List[DepthElem]] = Map.empty[MarketSide, List[DepthElem]]
  var depthSell: Map[MarketSide, List[DepthElem]] = Map.empty[MarketSide, List[DepthElem]]

  var userMap: Map[Long, User] = Map.empty[Long, User]

  def updateDepth() {
    marketUrlMap.map { kv =>
      val (sell, buy) = jsonToDepth(getDepthJsonByUrl(kv._2), kv._1)
      depthSell += kv._1 -> sell
      depthBuy += kv._1 -> buy
    }
    //
    //    println("========================")
    //    println(depthSell)
    //    println(depthBuy)
    //    println("------------------------")

  }

  def getInternalMarketDepth(side: MarketSide): Future[ApiMarketDepth] = {
    MarketService.getDepth(side, 10) map {
      case m =>
        val depth = m.data.get.asInstanceOf[ApiMarketDepth]
        depth
    }
  }

  def adjustMarket() {
    marketUrlMap.map { kv =>
      var side = MarketSide(kv._1._2, kv._1._1)
      MarketService.getDepth(side, 5) map {
        case m =>
          val depth = m.data.get.asInstanceOf[ApiMarketDepth]
          val internalLowestAsk = depth.asks.head.price.value
          val externalLowestAsk = depthSell(kv._1).head.price
          val internalHighestBid = depth.bids.head.price.value
          val externalHighestBid = depthBuy(kv._1).head.price

          try {
            if (internalHighestBid > externalLowestAsk) {
              var iterateList = depth.bids
              var quantity = 0.0
              while (!iterateList.isEmpty && iterateList.head.price.value - externalHighestBid >= 0) {
                quantity += iterateList.head.amount.value
                iterateList = iterateList.tail
              }
              quantity = quantityRoundByMarketSide(quantity, kv._1)

              userMap map {
                case kv =>
                  getAccount(kv._1, side._1.name.toUpperCase) map {
                    q =>
                      {
                        val reverseSide = MarketSide(side._2, side._1)
                        if (quantity > q) quantity = quantity - q * 0.5
                        val order = Some(UserOrder(kv._1.toString, Operations.Sell, side._1.name, side._2.name, Some(externalHighestBid), Some(quantityRoundByMarketSide(q * 0.5, reverseSide)), None))
                        Thread.sleep(Random.nextInt(3) * 1000)
                        println("adjust order : " + order)
                        submitOrder(order)
                      }
                  }
              }
              //              val order = UserOrder(adjustUserId.toString, Operations.Sell, kv._1._2.name, kv._1._1.name, Some(externalHighestBid), Some(quantity), None)
              //              println("submit adjust order : " + order)
              //              submitOrder(Some(order))
            }

            if (internalLowestAsk < externalHighestBid) {
              var iterateList = depth.asks
              var quantity = 0.0
              while (!iterateList.isEmpty && iterateList.head.price.value - externalHighestBid <= 0) {
                quantity += iterateList.head.amount.value
                iterateList = iterateList.tail
              }
              quantity = quantityRoundByMarketSide(quantity, kv._1)

              userMap map {
                case kv =>
                  getAccount(kv._1, side._2.name.toUpperCase) map {
                    q =>
                      {
                        val reverseSide = MarketSide(side._2, side._1)
                        if (quantity * externalHighestBid > q) quantity = quantity - q / externalHighestBid
                        val order = Some(UserOrder(kv._1.toString, Operations.Buy, side._1.name, side._2.name, Some(externalHighestBid), Some(quantityRoundByMarketSide(q / externalHighestBid, reverseSide)), None))
                        Thread.sleep(Random.nextInt(3) * 1000)
                        println("adjust order : " + order)
                        submitOrder(order)
                      }
                  }
              }
              val order = Some(UserOrder(adjustUserId.toString, Operations.Buy, kv._1._2.name, kv._1._1.name, Some(externalHighestBid), Some(quantity), None))
              println("adjust order : " + order)
              submitOrder(order)
            }
          } catch {
            case t: Throwable => println(t)
          }
      }
    }
  }

  def jsonToDepth(json: String, side: MarketSide): (List[DepthElem], List[DepthElem]) = {
    val jsonData = parseFull(json)
    val depthBuyList = transfer(jsonData.get.asInstanceOf[Map[String, Any]].get("bids").get.asInstanceOf[List[List[Any]]], side)
    val depthSellList = transfer(jsonData.get.asInstanceOf[Map[String, Any]].get("asks").get.asInstanceOf[List[List[Any]]], side)
    (depthSellList, depthBuyList.reverse)
  }

  def startExecutor() {

    // start update depth
    new Thread(new Runnable {
      def run() {
        while (true) {
          updateDepth()
          Thread.sleep(marketUpdateInterval)
        }
      }
    }).start()

    Thread.sleep(marketUpdateInterval * 2)

    // start depth adjust thread
    //    new Thread(new Runnable {
    //      def run() {
    //        while (true) {
    //          // merge depth
    //          Thread.sleep(20000)
    //          adjustMarket()
    //
    //        }
    //      }
    //    }).start()

    // add default users
    initDefaultUser

    userMap.map { kv =>

      new Thread(new Runnable {
        def run() {
          while (true) {
            createOrder(kv._1, kv._2) foreach {
              o => submitOrder(o)
            }
            val interval = kv._2.exchangeFrequency * 1000 / 2 + Random.nextInt(kv._2.exchangeFrequency * 1000)
            Thread.sleep(interval)
          }
        }
      }).start()
    }
  }

  //  def initDefaultUser() {
  //    userMap ++= Map(
  //      1000000000L -> User("dong"),
  //      1000000001L -> User("dong"),
  //      1000000002L -> User("xiaolu"),
  //      // 1000000001L -> User("dong3"))
  //
  //      1000000008L -> User("xiaolu"))
  //  }

  def initDefaultUser() {
    userMap ++= Map(

      1000000453L -> User("dong"),
      1000000340L -> User("xiaolu"),
      1000000004L -> User("chunming"),
      1000000008L -> User("xiaolu"))
  }

  def transfer(list: List[List[Any]], side: MarketSide): List[DepthElem] = {
    var depthList: List[DepthElem] = List()
    list.map { f =>
      val a = f(0) match {
        case i: String => roundByMarketSide(i.toDouble, side)
        case i: Double => roundByMarketSide(i, side)
        case _         => 0.0
      }
      val b = f(1) match {
        case i: String => roundByMarketSide(i.toDouble, side)
        case i: Double => roundByMarketSide(i, side)
        case _         => 0.0
      }
      depthList = DepthElem(a, b) :: depthList
    }
    depthList
  }

  def roundByMarketSide(src: Double, side: MarketSide) = {
    side match {
      case MarketSide(Btc, Ltc)  => roundDouble(src, 4)
      case MarketSide(Btc, Doge) => roundDouble(src, 8)
      case MarketSide(Btc, Bc)   => roundDouble(src, 8)
      case MarketSide(Btc, Drk)  => roundDouble(src, 6)
      case MarketSide(Btc, Vrc)  => roundDouble(src, 8)
      case MarketSide(Btc, Zet)  => roundDouble(src, 8)
    }
  }

  def quantityRoundByMarketSide(src: Double, side: MarketSide) = {
    side match {
      case MarketSide(Btc, Ltc)  => roundDouble(src, 4)
      case MarketSide(Btc, Doge) => roundDouble(src, 4)
      case MarketSide(Btc, Bc)   => roundDouble(src, 3)
      case MarketSide(Btc, Drk)  => roundDouble(src, 2)
      case MarketSide(Btc, Vrc)  => roundDouble(src, 3)
      case MarketSide(Btc, Zet)  => roundDouble(src, 3)
    }
  }

  def getDepthJsonByUrl(targetUrl: String): String = {
    val result = Http(url(targetUrl) OK as.String)
    result()
  }

  def getAccount(uid: Long, currency: String): Future[Double] = {
    var amount = 0.0
    val result = AccountService.getAccount(uid)
    result map {
      case m =>
        val account = m.data.get.asInstanceOf[ApiUserAccount]
        val value = account.accounts(currency).available.value
        value
    }
  }

  def createOrder(uid: Long, user: User): Future[Option[UserOrder]] = {
    val sides = marketUrlMap.keySet.toArray
    val side = sides(Random.nextInt(sides.size))
    val isTaker = hit(user.takerPercentage)

    val priceTerm = 5
    var price = 0.0
    var quantity = 0.0
    var operationsType = Operations.Buy

    (hit(user.takerPercentage), hit(user.buyPercentage)) match {
      case (true, true) => {
        operationsType = Operations.Buy
        val item = depthSell(side).drop(Random.nextInt(priceTerm)).head
        price = item.price
        quantity = randomQuantityBySide(side)
        getAccount(uid, side._1.name.toUpperCase) map {
          q =>
            {
              if (quantity * price > q * 0.1) quantity = quantityRoundByMarketSide(q * 0.1 / price, side)
              Some(UserOrder(uid.toString, operationsType, side._2.name, side._1.name, Some(price), Some(quantity), None))
            }
        }
      }
      case (true, false) => {
        operationsType = Operations.Sell
        val item = depthBuy(side).drop(Random.nextInt(priceTerm)).head
        price = item.price
        quantity = randomQuantityBySide(side)
        getAccount(uid, side._2.name.toUpperCase) map {
          q =>
            {
              println(quantity)
              println(price)
              println(q)
              if (quantity > q * 0.1) quantity = quantityRoundByMarketSide(q * 0.1, side)
              Some(UserOrder(uid.toString, operationsType, side._2.name, side._1.name, Some(price), Some(quantity), None))
            }
        }
      }
      case (false, true) => {
        operationsType = Operations.Buy
        val high = depthBuy(side).head.price
        val low = depthBuy(side).last.price
        price = roundByMarketSide(high - (high - low) * gaussRandom(high, low), side)
        if (price < 0.0) {
          price = roundByMarketSide(low, side)
        }

        var iterateList = depthBuy(side)
        while (iterateList.head.price > price) {
          quantity = iterateList.head.quantity
          iterateList = iterateList.tail
        }
        quantity = randomQuantityBySide(side)
        getAccount(uid, side._1.name.toUpperCase) map {
          q =>
            {
              if (quantity * price > q * 0.1) quantity = quantityRoundByMarketSide(q * 0.1 / price, side)
              Some(UserOrder(uid.toString, operationsType, side._2.name, side._1.name, Some(price), Some(quantity), None))
            }
        }
      }
      case (false, false) => {
        operationsType = Operations.Sell
        val high = depthSell(side).last.price
        val low = depthSell(side).head.price
        price = roundByMarketSide(low + (high - low) * gaussRandom(high, low), side)

        var iterateList = depthSell(side)
        while (iterateList.head.price < price) {
          quantity = iterateList.head.quantity
          iterateList = iterateList.tail
        }
        quantity = randomQuantityBySide(side)
        getAccount(uid, side._2.name.toUpperCase) map {
          q =>
            {
              if (quantity > q * 0.1) quantity = quantityRoundByMarketSide(q * 0.1, side)
              Some(UserOrder(uid.toString, operationsType, side._2.name, side._1.name, Some(price), Some(quantity), None))
            }
        }
      }
    }

  }

  def submitOrder(order: Option[UserOrder]) {
    if (order.isDefined && order.get.amount.get > 0.0) {
      println("submit order : " + order.get)
      AccountService.submitOrder(order.get)
    }
  }

  def randomQuantityBySide(side: MarketSide): Double = {
    side match {
      case MarketSide(Btc, Ltc)  => roundDouble(Random.nextDouble() / 5, 4)
      case MarketSide(Btc, Doge) => roundDouble(Random.nextDouble() * 10000, 4)
      case MarketSide(Btc, Bc)   => roundDouble(Random.nextDouble() * 10, 3)
      case MarketSide(Btc, Drk)  => roundDouble(Random.nextDouble() / 5, 2)
      case MarketSide(Btc, Vrc)  => roundDouble(Random.nextDouble() * 5, 3)
      case MarketSide(Btc, Zet)  => roundDouble(Random.nextDouble() * 5, 3)
    }
  }

  def hit(percent: Int): Boolean = {
    if (Random.nextInt(100) > percent)
      false
    else
      true
  }

  def roundDouble(src: Double, roundNum: Int): Double = {
    BigDecimal(src).setScale(roundNum, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  def gaussRandom(max: Double, min: Double): Double = {
    val temp = 12.0
    var x = 0.0
    0 to temp.toInt foreach { i =>
      x = x + Random.nextDouble
    }
    x = (x - temp / 2) / (Math.sqrt(temp * 1.5))
    Math.abs(x)
  }

}

object AutoExchangeRobotExecutor {

  def main(args: Array[String]) {
    val marketUrlMap: Map[MarketSide, String] = Map(
      Btc ~> Ltc -> "http://data.bter.com/api/1/depth/ltc_btc",
      Btc ~> Doge -> "http://data.bter.com/api/1/depth/doge_btc",
      Btc ~> Bc -> "http://data.bter.com/api/1/depth/bc_btc",
      Btc ~> Drk -> "http://data.bter.com/api/1/depth/drk_btc",
      Btc ~> Vrc -> "http://data.bter.com/api/1/depth/vrc_btc",
      Btc ~> Zet -> "http://data.bter.com/api/1/depth/zet_btc")
    val executor = new AutoExchangeRobotExecutor(marketUrlMap, 10000)
    executor.startExecutor
  }

  def start() {
    val marketUrlMap: Map[MarketSide, String] = Map(
      Btc ~> Ltc -> "http://data.bter.com/api/1/depth/ltc_btc",
      Btc ~> Doge -> "http://data.bter.com/api/1/depth/doge_btc",
      Btc ~> Bc -> "http://data.bter.com/api/1/depth/bc_btc",
      Btc ~> Drk -> "http://data.bter.com/api/1/depth/drk_btc",
      Btc ~> Vrc -> "http://data.bter.com/api/1/depth/vrc_btc",
      Btc ~> Zet -> "http://data.bter.com/api/1/depth/zet_btc")
    val executor = new AutoExchangeRobotExecutor(marketUrlMap, 10000)
    executor.startExecutor
  }

  def deposit() {
    //    val user1 = User(1000001001L, "xiaolu@coinport.com", None, "123456")
    //    val user2 = User(1000002001L, "jaice_229@163.com", None, "123456")
    //    val user3 = User(1000003001L, "mmmmmagina@163.com", None, "123456")
    //    UserService.register(user1)
    //    UserService.register(user2)
    //    UserService.register(user3)
    //    Thread.sleep(1000)
    AccountService.deposit(1000000000L, Btc, 1000.0)
    AccountService.deposit(1000000000L, Ltc, 1900)
    AccountService.deposit(1000000000L, Doge, 100000000.0)
    AccountService.deposit(1000000000L, Zet, 100000000.0)
    AccountService.deposit(1000000000L, Vrc, 100000000.0)
    AccountService.deposit(1000000001L, Btc, 1000.0)
    AccountService.deposit(1000000001L, Ltc, 3752)
    AccountService.deposit(1000000001L, Doge, 100000000.0)
    AccountService.deposit(1000000001L, Zet, 100000000.0)
    AccountService.deposit(1000000001L, Vrc, 100000000.0)
    AccountService.deposit(1000000002L, Btc, 1000.0)
    AccountService.deposit(1000000002L, Bc, 5000)
    AccountService.deposit(1000000002L, Drk, 5000)
    AccountService.deposit(1000000002L, Doge, 100000000.0)
    AccountService.deposit(1000000002L, Zet, 100000000.0)
    AccountService.deposit(1000000002L, Vrc, 100000000.0)
  }

}
