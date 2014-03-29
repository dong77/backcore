/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.data

import org.specs2.mutable._

class UserTransactionStateSpec extends Specification {
  "UserTransactionStateSpec" should {
    "add item into state and get them all" in {
      var state = UserTransactionState()
      val taker = 100L
      val maker = 101L
      val txs = (0 until 10) map (i => TransactionItem(i, i, i, i, taker, maker, true, i, i))
      txs.foreach { t =>
        state = state.addItem(taker, 1, t)
        state = state.addItem(maker, t.timestamp % 3, t)
      }

      //find taker & order results
      state.getItems(taker, Some(1), 0, 10) mustEqual txs.reverse

      //find maker & the orderId does not exsit
      state.getItems(maker, Some(-1), 0, 10) mustEqual Nil

      //find maker & orderId results
      state.getItems(maker, Some(0), 0, 10) mustEqual
        Seq(TransactionItem(0, 0, 0, 0, taker, maker, true, 0, 0),
          TransactionItem(3, 3, 3, 3, taker, maker, true, 3, 3),
          TransactionItem(6, 6, 6, 6, taker, maker, true, 6, 6),
          TransactionItem(9, 9, 9, 9, taker, maker, true, 9, 9)).reverse

      state.getItems(maker, Some(1), 0, 10) mustEqual
        Seq(TransactionItem(1, 1, 1, 1, taker, maker, true, 1, 1),
          TransactionItem(4, 4, 4, 4, taker, maker, true, 4, 4),
          TransactionItem(7, 7, 7, 7, taker, maker, true, 7, 7)).reverse

      state.getItems(maker, Some(2), 0, 10) mustEqual
        Seq(TransactionItem(2, 2, 2, 2, taker, maker, true, 2, 2),
          TransactionItem(5, 5, 5, 5, taker, maker, true, 5, 5),
          TransactionItem(8, 8, 8, 8, taker, maker, true, 8, 8)).reverse
    }
  }
}
