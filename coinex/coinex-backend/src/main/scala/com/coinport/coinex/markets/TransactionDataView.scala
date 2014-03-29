/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import com.coinport.coinex.common.{ StateManager, ExtendedView }
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import akka.persistence.Persistent
import Implicits._

class TransactionDataView(market: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mup"
  override val viewId = "tx_data_view"
  private val manager = new TransactionDataManager(market)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      txs foreach (t => manager.addItem(t, orderInfo.side == market))

    case QueryTransactionData(side, from, num) if side == market || side == market.reverse =>
      sender ! manager.getTransactionData(side == market, from, num)
  }
}

class TransactionDataManager(market: MarketSide) extends StateManager[TransactionDataState] {
  initWithDefaultState(TransactionDataState())

  def addItem(t: Transaction, sameSide: Boolean) {
    val amount = Math.abs(t.takerUpdate.current.quantity - t.takerUpdate.previous.quantity)
    val reverseAmount = Math.abs(t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity)

    val reversePrice = amount.toDouble / reverseAmount.toDouble
    val price = 1 / reversePrice

    val (taker, tOrderId) = (t.takerUpdate.current.userId, t.takerUpdate.current.id)
    val (maker, mOrderId) = (t.makerUpdate.current.userId, t.makerUpdate.current.id)

    val item = TransactionItem(t.timestamp, price, amount, reverseAmount, taker, maker, sameSide, tOrderId, mOrderId)
    val reverseItem = TransactionItem(t.timestamp, reversePrice, reverseAmount, amount, taker, maker, sameSide, tOrderId, mOrderId)

    if (sameSide) {
      state = state.addItem(item)
      state = state.addReverseItem(reverseItem)
    } else {
      state = state.addItem(reverseItem)
      state = state.addReverseItem(item)
    }
  }

  def getTransactionData(sameSide: Boolean, from: Long, num: Int): TransactionData = {
    if (sameSide) TransactionData(state.getItems(from, num))
    else TransactionData(state.getReverseItems(from, num))
  }
}
