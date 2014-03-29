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

class UserTransactionView(market: MarketSide) extends ExtendedView {
  override val processorId = "coinex_mup"
  override val viewId = "user_transaction_view"
  private val manager = new UserTransactionManager(market)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      manager.addItem(orderInfo, txs)

    case QueryUserTransaction(side, userId, orderId, from, num) if side == market || side == market.reverse =>
      sender ! manager.getUserTransaction(side == market, userId, orderId, from, num)
  }
}

class UserTransactionManager(market: MarketSide) extends StateManager[UserTransactionState] {
  initWithDefaultState(UserTransactionState())

  def addItem(orderInfo: OrderInfo, txs: Seq[Transaction]) {
    val sameSide = orderInfo.side == market

    txs.foreach { t =>
      val amount = Math.abs(t.takerUpdate.current.quantity - t.takerUpdate.previous.quantity)
      val reverseAmount = Math.abs(t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity)

      val price = reverseAmount.toDouble / amount.toDouble
      val reversePrice = 1 / price

      val (taker, tOrderId) = (t.takerUpdate.current.userId, t.takerUpdate.current.id)
      val (maker, mOrderId) = (t.makerUpdate.current.userId, t.makerUpdate.current.id)

      val item = TransactionItem(t.timestamp, price, amount, reverseAmount, taker, maker, sameSide, tOrderId, mOrderId)
      val reverseItem = TransactionItem(t.timestamp, reversePrice, reverseAmount, amount, taker, maker, sameSide, tOrderId, mOrderId)

      if (sameSide) {
        state = state.addItem(taker, tOrderId, item)
        state = state.addItem(maker, mOrderId, item)
        state = state.addReverseItem(taker, tOrderId, reverseItem)
        state = state.addReverseItem(maker, mOrderId, reverseItem)
      } else {
        state = state.addItem(taker, tOrderId, reverseItem)
        state = state.addItem(maker, mOrderId, reverseItem)
        state = state.addReverseItem(taker, tOrderId, item)
        state = state.addReverseItem(maker, mOrderId, item)
      }
    }
  }

  def getUserTransaction(sameSide: Boolean, userId: Long, orderId: Option[Long], from: Long, num: Int): TransactionData = {
    if (sameSide) TransactionData(state.getItems(userId, orderId, from, num))
    else TransactionData(state.getReverseItems(userId, orderId, from, num))
  }
}
