/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
import Implicits._

class MarketUserLogsView extends ExtendedView {
  override def processorId = "coinex_mup"
  private val manager = new MarketUserLogsManager

  def receive = {
    case DebugDump =>
      log.info("state: {}", manager())

    case x =>
      log.info("~~~ saw: " + x)
      if (receiveMessage.isDefinedAt(x)) receiveMessage(x)
  }

  def receiveMessage: Receive = {
    case Persistent(OrderCancelled(_, order), _) => manager.cancelOrder(order)

    case Persistent(m: OrderSubmitted, _) =>
      manager.addOrUpdateOrderInfo(m.originOrderInfo)
      m.txs.foreach { tx =>
        val outAmount = tx.makerUpdate.previous.quantity - tx.makerUpdate.current.quantity
        val inAmount = tx.takerUpdate.previous.quantity - tx.takerUpdate.current.quantity
        val status =
          if (tx.makerUpdate.current.isFullyExecuted == 0) OrderStatus.FullyExecuted
          else OrderStatus.PartiallyExecuted

        val orderInfo = OrderInfo(
          m.originOrderInfo.side.reverse,
          tx.makerUpdate.current,
          outAmount, inAmount,
          status, Some(tx.timestamp))
          
        manager.addOrUpdateOrderInfo(orderInfo)
      }

    case q: QueryUserOrders =>
      sender ! QueryUserOrdersResult(q.userId, manager.getOrderInfos(q))
  }
}