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

class UserOrdersView extends ExtendedView {
  override def processorId = "coinex_mup"
  private val manager = new UserOrdersManager

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

    case q: QueryUserOrders =>
      sender ! QueryUserOrdersResult(q.userId, manager.getOrders(q))
  }
}

class UserOrdersManager extends StateManager[UserLogsState] {
  initWithDefaultState(UserLogsState())

  def getOrders(param: QueryUserOrders): Seq[OrderInfo] = {
    def eval(orderInfo: OrderInfo) = (param.status.isEmpty || param.status.get == orderInfo.status)
    state.orderInfoMap.getOrElse(param.userId, Nil)
      .filter(eval)
      .drop(param.skipOrders.getOrElse(0))
      .take(param.numOrders.getOrElse(100))
  }

  def addOrder(oi: OrderInfo) = {
    val userId = oi.order.userId
    val orderId = oi.order.id
    var orderInfos = oi +: state.orderInfoMap.getOrElse(userId, Nil)
    state = state.copy(orderInfoMap = state.orderInfoMap + (userId -> orderInfos))
  }

  def updateOrder(oi: OrderInfo) = {
    val userId = oi.order.userId
    val orderId = oi.order.id
    var orderInfos = state.orderInfoMap.getOrElse(userId, Nil)
    val idx = orderInfos.indexWhere(_.order.id == orderId)
    val (head, tail) = orderInfos.splitAt(idx)
    orderInfos = head ++ (mergeOrderInfos(tail(0), oi) +: tail.drop(1))
    state = state.copy(orderInfoMap = state.orderInfoMap + (userId -> orderInfos))
  }

  def cancelOrder(order: Order) = {
    val orderInfos = state.orderInfoMap.getOrElse(order.userId, Nil) map { oi =>
      if (oi.order.id == order.id) oi.copy(status = OrderStatus.Cancelled) else oi
    }
    state = state.copy(orderInfoMap = state.orderInfoMap + (order.userId -> orderInfos))
  }

  private def mergeOrderInfos(old: OrderInfo, neu: OrderInfo): OrderInfo = {
    old.copy(
      status = neu.status,
      outAmount = old.outAmount + neu.outAmount,
      inAmount = old.inAmount + neu.inAmount,
      lastTxTimestamp = neu.lastTxTimestamp)
  }
}