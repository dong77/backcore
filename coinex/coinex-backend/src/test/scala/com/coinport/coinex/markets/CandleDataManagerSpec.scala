/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable._
import com.coinport.coinex.data._
import Implicits._
import Currency._
import com.coinport.coinex.data.ChartTimeDimension._

class CandleDataManagerSpec extends Specification {
  "ChartDataManagerSpec" should {

    "save data from order submitted" in {
      val market = Btc ~> Rmb
      val manager = new CandleDataManager(market)
      val makerPrevious = Order(userId = 555, id = 1, price = Some(1.0 / 3000), quantity = 3000, takeLimit = None, timestamp = Some(0))
      val makerCurrent = Order(userId = 555, id = 2, price = Some(1.0 / 3000), quantity = 0, takeLimit = None, timestamp = Some(0))
      val takerPrevious = Order(userId = 888, id = 3, price = Some(3000), quantity = 0, timestamp = Some(0))
      val takerCurrent = Order(userId = 888, id = 4, price = Some(3000), quantity = 1, timestamp = Some(0))

      val t = Transaction(1L, 1000000, market, OrderUpdate(takerPrevious, takerCurrent), OrderUpdate(makerPrevious, makerCurrent))

      manager.addItem(t, true)

      manager.getCandleData(true, OneMinute, 800000, 1200000).items mustEqual
        Seq(CandleDataItem(16, 1, 3000.0, 3000.0, 3000.0, 3000.0))
    }
  }
}
