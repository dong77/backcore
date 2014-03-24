/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.data

import org.specs2.mutable._

class TransactionDataStateSpec extends Specification {
  "TransactionDataStateSpec" should {
    "add item into state and get them all" in {
      var state = TransactionDataState()
      val txs = (0 until 10) map (i => TransactionItem(i, i, i, i))
      txs.foreach(t => state = state.addItem(t.timestamp, t.price, t.volumn, t.amount))

      state.getItems(0, 2) mustEqual (Seq(TransactionItem(9, 9, 9, 9), TransactionItem(8, 8, 8, 8)))
      state.getItems(0, 10) mustEqual txs.reverse
    }

    "will auto archive redundant data" in {
      val archive = 50
      val maxMaintain = 150
      var state = TransactionDataState(archive, maxMaintain, Seq.empty[TransactionItem])
      (0 until 210).foreach {
        i =>
          state = state.addItem(i, i, i, i)
          if (i == 170) state.transactionItems.size mustEqual (121)
      }

      state.transactionItems.size mustEqual 110
    }
  }
}
