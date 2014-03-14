/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.postmarket

import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
import Implicits._

private[postmarket] class CandleDataManager extends StateManager[CandleDataState] {
  initWithDefaultState(CandleDataState())

  val minute = 60 * 1000
  val quarter = 15 * minute
  val hour = 4 * quarter
  val day = 24 * hour

  def extract(mu: MarketUpdate) = {
    val side = mu.originOrderInfo.side
    val order = mu.originOrderInfo.order
    val timestamp = order.timestamp.get
    //   val volumn = mu.outAmount // volumn as side.outCurrency

    val time = timestamp / minute
    for {
      first <- mu.firstPrice
      high = first
      last <- mu.lastPrice
      low = last
    } {
      // Since all orders are sell orders, first price will always be the highest price,
      // and last price will always be the lowest price
      val item = CandleDataItem(time, mu.outAmount, first, last, low, high)

    }

  }
}