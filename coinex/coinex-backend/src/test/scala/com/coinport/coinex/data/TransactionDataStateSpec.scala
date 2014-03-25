/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.data

import org.specs2.mutable._

class TransactionDataStateSpec extends Specification {
  "TransactionDataStateSpec" should {
    "add item into state and get them all" in {
      var state = TransactionDataState()
      val txs = (0 until 10) map (i => TransactionItem(i, i, i, i, i, i, true))
      txs.foreach(t => state = state.addItem(t))

      state.getItems(0, 2) mustEqual (Seq(TransactionItem(9, 9, 9, 9, 9, 9, true), TransactionItem(8, 8, 8, 8, 8, 8, true)))
      state.getItems(0, 10) mustEqual txs.reverse
    }

    "will auto archive redundant data" in {
      val archive = 50
      val maxMaintain = 150
      var state = TransactionDataState(archive, maxMaintain, Seq.empty[TransactionItem])
      (0 until 210).foreach {
        i =>
          val item = TransactionItem(i, i, i, i, i, i, true)
          state = state.addItem(item)
          if (i == 170) state.transactionItems.size mustEqual (121)
      }

      state.transactionItems.size mustEqual 110
    }
  }
}
