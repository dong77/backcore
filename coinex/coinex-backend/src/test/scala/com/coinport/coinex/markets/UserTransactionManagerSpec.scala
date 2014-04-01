/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable.Specification
import com.coinport.coinex.data.Currency.{ Rmb, Btc }
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.data._
import scala.Some

class UserTransactionManagerSpec extends Specification {
  "UserTransactionStateSpec" should {
    "save data from order submitted" in {
      val market = Btc ~> Rmb
      val manager = new UserTransactionManager(market)

      val txs = (0 until 3) map {
        i =>
          val makerPrevious = Order(userId = 555, id = i, price = Some(1.0 / 3000), quantity = 3000, takeLimit = None, timestamp = Some(0))
          val makerCurrent = Order(userId = 555, id = i, price = Some(1.0 / 3000), quantity = 0, takeLimit = None, timestamp = Some(0))
          val takerPrevious = Order(userId = 888, id = 2 * i, price = Some(3000), quantity = 0, timestamp = Some(0))
          val takerCurrent = Order(userId = 888, id = 2 * i, price = Some(3000), quantity = 1, timestamp = Some(0))
          Transaction(i, i, market, OrderUpdate(takerPrevious, takerCurrent), OrderUpdate(makerPrevious, makerCurrent))
      }

      val orderInfo = OrderInfo(market,
        Order(userId = 555, id = 0, price = Some(1.0 / 3000), quantity = 3000, takeLimit = None, timestamp = Some(0)),
        0, 0, OrderStatus(1))

      manager.addItem(orderInfo, txs)

      manager.getUserTransaction(true, 555, None, 0, 100) mustEqual
        TransactionData(List(TransactionItem(2, 3000.0, 1, 3000, 888, 555, true, 4, 2),
          TransactionItem(1, 3000.0, 1, 3000, 888, 555, true, 2, 1),
          TransactionItem(0, 3000.0, 1, 3000, 888, 555, true, 0, 0)))

      manager.getUserTransaction(true, 888, None, 0, 100) mustEqual
        TransactionData(List(TransactionItem(2, 3000.0, 1, 3000, 888, 555, true, 4, 2),
          TransactionItem(1, 3000.0, 1, 3000, 888, 555, true, 2, 1),
          TransactionItem(0, 3000.0, 1, 3000, 888, 555, true, 0, 0)))
    }
  }
}
