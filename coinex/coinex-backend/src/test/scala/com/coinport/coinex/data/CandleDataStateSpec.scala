/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.data

import org.specs2.mutable._
import com.coinport.coinex.data.ChartTimeDimension._

class CandleDataStateSpec extends Specification {
  "CandleDataStateSpec" should {

    "save candle data into state and can get them by time dimension" in {
      var state = CandleDataState()

      state.getItems(OneMinute, 0, 0) mustEqual Seq.empty[CandleDataItem]
      state.getReverseItems(OneMinute, 0, 0) mustEqual Seq.empty[CandleDataItem]

      val txs = Seq(
        (10000000, 100.0, 1 / 100, 2, 200),
        (10000001, 200.0, 1 / 200, 2, 400),
        (20000000, 50.0, 1 / 50, 2, 100),
        (20000001, 20.0, 1 / 20, 2, 40),
        (30000000, 200.0, 1 / 200, 2, 400),
        (30000001, 400.0, 1 / 400, 2, 800),
        (40000000, 60.0, 1 / 60, 2, 120),
        (40000001, 40.0, 1 / 40, 2, 80))

      txs.foreach { t =>
        state = state.addItem(OneMinute, t._1, t._2, t._4)
        state = state.addReverseItem(OneMinute, t._1, t._3, t._5)
      }

      state.getItems(OneMinute, 40000001, 40000000) mustEqual Seq(CandleDataItem(666, 4, 60.0, 40.0, 40.0, 60.0))
      state.getItems(OneMinute, 30000001, 30000000) mustEqual Seq(CandleDataItem(500, 4, 200.0, 400.0, 200.0, 400.0))
      state.getItems(OneMinute, 20000001, 20000000) mustEqual Seq(CandleDataItem(333, 4, 50.0, 20.0, 20.0, 50.0))
      state.getItems(OneMinute, 10000001, 10000000) mustEqual Seq(CandleDataItem(166, 4, 100.0, 200.0, 100.0, 200.0))

      txs.foreach { t =>
        state = state.addItem(OneDay, t._1, t._2, t._4)
        state = state.addReverseItem(OneDay, t._1, t._3, t._5)
      }

      state.getItems(OneDay, 40000001, 30000001) mustEqual state.getItems(OneDay, 20000001, 0)
    }
  }
}
