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
  override val viewId = "transaction_data_view"
  private val manager = new TransactionDataManager(market)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      txs foreach (t => manager.addItem(t, orderInfo.side == market))

    case QueryTransactionData(side, from, num) if side == market || side != market =>
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

    val taker = t.takerUpdate.current.userId
    val maker = t.takerUpdate.current.userId

    if (sameSide) {
      var item = TransactionItem(t.timestamp, price, amount, reverseAmount, taker, maker, sameSide)
      state = state.addItem(item)
      item = TransactionItem(t.timestamp, reversePrice, reverseAmount, amount, taker, maker, sameSide)
      state = state.addReverseItem(item)
    } else {
      var item = TransactionItem(t.timestamp, reversePrice, reverseAmount, amount, taker, maker, sameSide)
      state = state.addItem(item)
      item = TransactionItem(t.timestamp, price, amount, reverseAmount, taker, maker, sameSide)
      state = state.addReverseItem(item)
    }
  }

  def getTransactionData(sameSide: Boolean, from: Long, num: Int): TransactionData = {
    if (sameSide) TransactionData(state.getItems(from, num))
    else TransactionData(state.getReverseItems(from, num))
  }
}
