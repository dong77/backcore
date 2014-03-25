/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
import Implicits._

class CandleDataView(market: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mup"
  override val viewId = "candle_data_view"
  private val manager = new CandleDataManager(market)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      txs foreach (t => manager.addItem(t, orderInfo.side == market))

    case QueryCandleData(side, dimension, from, to) if side == market || side == market.reverse =>
      sender ! manager.getCandleData(side == market, dimension, from, to)
  }
}

class CandleDataManager(market: MarketSide) extends StateManager[CandleDataState] {
  initWithDefaultState(CandleDataState())

  def addItem(t: Transaction, sameSide: Boolean) {
    val amount = Math.abs(t.takerUpdate.current.quantity - t.takerUpdate.previous.quantity)
    val reverseAmount = Math.abs(t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity)

    val reversePrice = amount.toDouble / reverseAmount.toDouble
    val price = 1 / reversePrice

    if (sameSide) {
      ChartTimeDimension.list.foreach(d => state = state.addItem(d, t.timestamp, price, amount))
      ChartTimeDimension.list.foreach(d => state = state.addReverseItem(d, t.timestamp, reversePrice, reverseAmount))
    } else {
      ChartTimeDimension.list.foreach(d => state = state.addItem(d, t.timestamp, reversePrice, reverseAmount))
      ChartTimeDimension.list.foreach(d => state = state.addReverseItem(d, t.timestamp, price, amount))
    }
  }

  def getCandleData(sameSide: Boolean, dimension: ChartTimeDimension, from: Long, to: Long) = {
    val start = Math.min(from, to)
    val stop = Math.max(from, to)
    if (sameSide) CandleData(System.currentTimeMillis(), state.getItems(dimension, start, stop))
    else CandleData(System.currentTimeMillis(), state.getReverseItems(dimension, start, stop))
  }
}
