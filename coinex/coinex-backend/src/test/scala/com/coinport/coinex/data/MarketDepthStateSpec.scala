/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.data

import org.specs2.mutable._
import MarketState._
import Implicits._
import Currency._

class MarketDepthStateSpec extends Specification {
  "MarketDepthStateSpec" should {

    "sorted asks from low to high and merge amount if price match" in {
      var state = MarketDepthState()
      state = state.adjustAsk(10.0, 1).adjustAsk(11.0, 1).adjustAsk(11.0, 1)
      state.get(10) mustEqual (Seq(MarketDepthItem(10.0, 1), MarketDepthItem(11.0, 2)), Nil)

      state = state.adjustAsk(10.0, -1).adjustAsk(11.0, -1).adjustAsk(13.0, 1)
      state.get(10) mustEqual (Seq(MarketDepthItem(11.0, 1), MarketDepthItem(13.0, 1)), Nil)
      state.get(1) mustEqual (Seq(MarketDepthItem(11.0, 1)), Nil)
    }

    "sorted bids from high to low and merge amount if price match" in {
      var state = MarketDepthState()
      state = state.adjustBid(1 / 10.0, 1).adjustBid(1 / 11.0, 1).adjustBid(1 / 11.0, 1)
      state.get(10) mustEqual (Nil, Seq(MarketDepthItem(11.0, 2), MarketDepthItem(10.0, 1)))

      state = state.adjustBid(1 / 10.0, -1).adjustBid(1 / 11.0, -1).adjustBid(1 / 9.0, 1)
      state.get(10) mustEqual (Nil, Seq(MarketDepthItem(11.0, 1), MarketDepthItem(9.0, 1)))
      state.get(1) mustEqual (Nil, Seq(MarketDepthItem(11.0, 1)))
    }
  }
}