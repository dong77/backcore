package com.coinport.coinex.integration

import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import scala.concurrent.duration._
import akka.actor.actorRef2Scala

class MarketIntegrationSpec extends IntegrationSpec(new Environment) {
  import env._

  val market = MarketSide(Btc, Cny)
  val reverse = MarketSide(Cny, Btc)
  "CoinexApp" must {
    "submit orders and adjust account amounts correctly" in {
      val user1 = 1000L
      val user2 = 2000L
      // deposit 6000 CNY
      val depositRmb = AccountTransfer(1, user1, TransferType.Deposit, Cny, 6000 * 1000, TransferStatus.Pending)
      client ! DoRequestTransfer(depositRmb)
      val RequestTransferSucceeded(d1) = receiveOne(4 seconds)

      client ! AdminConfirmTransferSuccess(d1)
      val ok1 = receiveOne(4 seconds)
      ok1 should be(AdminCommandResult(ErrorCode.Ok))

      // deposit 10 BTC
      val depositBtc = AccountTransfer(2, user2, TransferType.Deposit, Btc, 10 * 1000, TransferStatus.Pending)
      client ! DoRequestTransfer(depositBtc)
      val RequestTransferSucceeded(d2) = receiveOne(4 seconds)

      client ! AdminConfirmTransferSuccess(d2)
      val ok2 = receiveOne(4 seconds)
      ok2 should be(AdminCommandResult(ErrorCode.Ok))
      Thread.sleep(200)

      client ! QueryAccount(user1)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(1000, Map(Cny -> CashAccount(Cny, 6000000, 0, 0)))))

      client ! QueryAccount(user2)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(2000, Map(Btc -> CashAccount(Btc, 10000, 0, 0)))))

      // submit a sell order
      val sellBtc = Order(userId = user2, id = 0L, quantity = 10000, price = Some(500), takeLimit = Some(4500000))
      client ! DoSubmitOrder(market, sellBtc)

      val resultSellBtc = receiveOne(4 seconds)
      println(resultSellBtc)
      Thread.sleep(500)

      client ! QueryAccount(user1)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(1000, Map(Cny -> CashAccount(Cny, 6000000, 0, 0)))))

      client ! QueryAccount(user2)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(2000, Map(Btc -> CashAccount(Btc, 1000, 9000, 0)))))

      // submit a buy order
      val buyBtc = Order(userId = user1, id = 0L, quantity = 6000000, price = Some(1.0 / 600), takeLimit = Some(10000))
      client ! DoSubmitOrder(reverse, buyBtc)

      val resultBuyBtc = receiveOne(4 seconds)
      println(resultBuyBtc)
      Thread.sleep(500)

      client ! QueryAccount(user1)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(1000, Map(Cny -> CashAccount(Cny, 900000, 600000, 0), Btc -> CashAccount(Btc, 8991, 0, 0)))))

      client ! QueryAccount(user2)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(2000, Map(Btc -> CashAccount(Btc, 1000, 0, 0), Cny -> CashAccount(Cny, 4495500, 0, 0)))))
    }
  }
}