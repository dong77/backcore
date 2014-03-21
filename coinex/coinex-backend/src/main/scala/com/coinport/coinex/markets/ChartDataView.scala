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
import com.coinport.coinex.data.ReturnChartType
import scala.Some

class ChartDataView(market: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mup"

  private val manager = new ChartDataManager(market)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      txs foreach { t =>
        println("transaction>>>>>>>>>>>>>>>>>\n" + t)
        manager.addItem(t, orderInfo.side != market)
      }

    case QueryChartData(side, dimension, from, maxDepth, returnType) =>
      sender ! manager.getChartData(side, dimension, from, maxDepth, returnType)
  }
}

class ChartDataManager(market: MarketSide) extends StateManager[CandleDataState] {
  initWithDefaultState(CandleDataState())

  def addItem(t: Transaction, reverse: Boolean) {
    val amount = t.takerUpdate.current.quantity - t.takerUpdate.previous.quantity
    val reverseAmount = t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity

    val reversePrice = Math.abs(amount.toDouble / reverseAmount.toDouble)
    val price = 1 / reversePrice

    if (!reverse) {
      ChartTimeDimension.list.foreach(d => state = state.addItem(d, t.timestamp, price, amount))
      ChartTimeDimension.list.foreach(d => state = state.addReverseItem(d, t.timestamp, reversePrice, reverseAmount))
    } else {
      ChartTimeDimension.list.foreach(d => state = state.addItem(d, t.timestamp, reversePrice, reverseAmount))
      ChartTimeDimension.list.foreach(d => state = state.addReverseItem(d, t.timestamp, price, amount))
    }
  }

  def getChartData(side: MarketSide, dimension: ChartTimeDimension, from: Long, to: Long, ctype: ReturnChartType) = {
    val reverse = market != side

    ChartData(System.currentTimeMillis(),
      if (ctype.retrunChandle) Some(getCandleChart(reverse, dimension, from, to)) else None)
  }

  private def getCandleChart(reverse: Boolean, dimension: ChartTimeDimension, from: Long, to: Long): Seq[CandleDataItem] = {
    val start = Math.min(from, to)
    val stop = Math.max(from, to)
    if (reverse) state.getReverseItem(dimension, start, stop)
    else state.getItem(dimension, start, stop)
  }
}
