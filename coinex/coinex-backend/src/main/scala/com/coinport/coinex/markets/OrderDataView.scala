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

class OrderDataView(market: MarketSide) extends ExtendedView {
  override val processorId = "coinex_mup"
  override val viewId = "orders_data_view"
  private val manager = new OrderDataManager(market)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderCancelled(_, order), _) => manager.cancelOrder(order)

    case Persistent(m: OrderSubmitted, _) if (m.originOrderInfo.side == market || m.originOrderInfo.side != market.reverse) =>
      manager.addOrder(m.originOrderInfo)
      m.txs.foreach { tx =>
        val outAmount = tx.makerUpdate.previous.quantity - tx.makerUpdate.current.quantity
        val inAmount = tx.takerUpdate.previous.quantity - tx.takerUpdate.current.quantity
        val status =
          if (tx.makerUpdate.current.isFullyExecuted) OrderStatus.FullyExecuted
          else OrderStatus.PartiallyExecuted

        val orderInfo = OrderInfo(
          m.originOrderInfo.side.reverse,
          tx.makerUpdate.current,
          outAmount, inAmount,
          status, Some(tx.timestamp))

        manager.updateOrder(orderInfo)
      }

    case QueryOrderData(side: MarketSide, orderId: Option[Long], from: Long, to: Long) if (side == market) =>
      sender ! QueryOrderDataResult(manager.getOrders(orderId, from, to))
  }
}

class OrderDataManager(side: MarketSide) extends StateManager[OrderDataState] {
  initWithDefaultState(OrderDataState())

  def getOrders(orderId: Option[Long], from: Long, to: Long): Seq[OrderInfo] = {
    state.getItem(orderId, from, to)
  }

  def addOrder(oi: OrderInfo) =
    state = state.addItem(oi.order.id, oi)

  def updateOrder(oi: OrderInfo) = {
    state = state.updateItem(oi.order.id, oi)
  }

  def cancelOrder(order: Order) = {
    state = state.cancelItem(order.id)
  }
}
