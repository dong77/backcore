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

class CandleDataView extends ExtendedView {
  override def processorId = "coinex_pmp"
  private val manager = new CandleDataBundlesManager

  def receive = {
    case DebugDump =>
      log.info("state: {}", manager())

    case x =>
      log.info("~~~ saw: " + x)
      if (receiveMessage.isDefinedAt(x)) receiveMessage(x)
  }

  def receiveMessage: Receive = {
    case Persistent(mu: MarketUpdate, _) if mu.txs.nonEmpty =>
    //  val timestamp = mu.originOrderInfo.order.timestamp.get
    case q: QueryMarketCandleData =>
  }
}

private class CandleDataBundlesManager extends StateManager[CandleDataBundles] {
  initWithDefaultState(CandleDataBundles())

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