package com.coinport.coinex.integration

import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import scala.concurrent.duration._
import akka.actor.actorRef2Scala

class MarketIntegrationSpec extends IntegrationSpec(new Environment) {
  import env._

  val market = MarketSide(Btc, Rmb)
  val reverse = MarketSide(Rmb, Btc)
  "CoinexApp" must {
    "submit orders and adjust account amounts correctly" in {
      val user1 = 1000L
      val user2 = 2000L
      // deposit 60000 RMB
      val depositRmb = AccountTransfer(1, user1, TransferType.Deposit, Rmb, 60000, TransferStatus.Pending)
      client ! DoRequestTransfer(depositRmb)
      val RequestTransferSucceeded(d1) = receiveOne(4 seconds)

      client ! AdminConfirmTransferSuccess(d1)
      val ok1 = receiveOne(4 seconds)
      ok1 should be(AdminCommandResult(ErrorCode.Ok))

      // deposit 10 BTC
      val depositBtc = AccountTransfer(2, user2, TransferType.Deposit, Btc, 10, TransferStatus.Pending)
      client ! DoRequestTransfer(depositBtc)
      val RequestTransferSucceeded(d2) = receiveOne(4 seconds)

      client ! AdminConfirmTransferSuccess(d2)
      val ok2 = receiveOne(4 seconds)
      ok2 should be(AdminCommandResult(ErrorCode.Ok))
      Thread.sleep(200)

      client ! QueryAccount(user1)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(1000, Map(Rmb -> CashAccount(Rmb, 60000, 0, 0)))))

      client ! QueryAccount(user2)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(2000, Map(Btc -> CashAccount(Btc, 10, 0, 0)))))

      // submit a sell order
      val sellBtc = Order(userId = user2, id = 0L, quantity = 10, price = Some(5000), takeLimit = Some(45000))
      client ! DoSubmitOrder(market, sellBtc)

      val resultSellBtc = receiveOne(4 seconds)
      println(resultSellBtc)
      Thread.sleep(500)

      client ! QueryAccount(user1)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(1000, Map(Rmb -> CashAccount(Rmb, 60000, 0, 0)))))

      client ! QueryAccount(user2)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(2000, Map(Btc -> CashAccount(Btc, 1, 9, 0)))))

      // submit a buy order
      val buyBtc = Order(userId = user1, id = 0L, quantity = 60000, price = Some(1.0 / 6000), takeLimit = Some(10))
      client ! DoSubmitOrder(reverse, buyBtc)

      val resultBuyBtc = receiveOne(4 seconds)
      println(resultBuyBtc)
      Thread.sleep(100)

      client ! QueryAccount(user1)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(1000, Map(Rmb -> CashAccount(Rmb, 9000, 6000, 0), Btc -> CashAccount(Btc, 9, 0, 0)))))

      client ! QueryAccount(user2)
      receiveOne(4 seconds) should be(QueryAccountResult(UserAccount(2000, Map(Btc -> CashAccount(Btc, 1, 0, 0), Rmb -> CashAccount(Rmb, 44955, 0, 0)))))
    }
  }
}