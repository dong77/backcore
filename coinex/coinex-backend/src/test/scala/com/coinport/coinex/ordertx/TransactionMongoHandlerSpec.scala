/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.ordertx

import com.coinport.coinex.data.Currency.{ Rmb, Btc }
import com.coinport.coinex.data.{ Cursor, QueryTransaction, TransactionItem }
import com.coinport.coinex.common.EmbeddedMongoForTestWithBF
import com.coinport.coinex.data.Implicits._

class TransactionMongoHandlerSpec extends EmbeddedMongoForTestWithBF {
  val market = Btc ~> Rmb

  class TransactionClass extends TransactionMongoHandler {
    val coll = database("transaction")
  }

  "TransactionHandlerSpec" should {
    val transactionClass = new TransactionClass()

    "add item into state and get them all" in {
      transactionClass.coll.drop()
      val txs = (0 until 10) map (i => TransactionItem(i, i, i, i, i, i, i, i, market, i))
      txs.foreach(t => transactionClass.addItem(t))

      var q = QueryTransaction(cursor = Cursor(0, 2), getCount = false)
      transactionClass.getItems(q).map(_.tid) should equal(Seq(9, 8))

      q = QueryTransaction(cursor = Cursor(0, 1), getCount = true)
      transactionClass.countItems(q) should be(10)

      q = QueryTransaction(cursor = Cursor(0, 100), getCount = false)
      transactionClass.getItems(q).map(_.tid) should equal(Seq(9, 8, 7, 6, 5, 4, 3, 2, 1, 0))
    }
  }

}
