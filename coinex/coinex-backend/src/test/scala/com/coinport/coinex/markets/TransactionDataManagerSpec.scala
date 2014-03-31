/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable.Specification
import com.coinport.coinex.data.Currency.{ Rmb, Btc }
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.data.{ OrderUpdate, Transaction, Order }

class TransactionDataManagerSpec extends Specification {
  "TransactionDataManagerSpec" should {
    "save data from order submitted" in {
      val market = Btc ~> Rmb
      val manager = new TransactionDataManager(market)

      val txs = (0 until 100) map { i =>
        val makerPrevious = Order(userId = 555, id = 1, price = Some(1.0 / 3000), quantity = 3000, takeLimit = None, timestamp = Some(0))
        val makerCurrent = Order(userId = 555, id = 2, price = Some(1.0 / 3000), quantity = 0, takeLimit = None, timestamp = Some(0))
        val takerPrevious = Order(userId = 888, id = 3, price = Some(3000), quantity = 0, timestamp = Some(0))
        val takerCurrent = Order(userId = 888, id = 4, price = Some(3000), quantity = 1, timestamp = Some(0))
        Transaction(i, i, OrderUpdate(takerPrevious, takerCurrent), OrderUpdate(makerPrevious, makerCurrent))
      }

      txs.foreach(t => manager.addItem(t, true))
      manager.getTransactionData(true, 0, 3).items.map(_.timestamp) mustEqual Seq(99, 98, 97)
    }
  }
}
