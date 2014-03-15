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

class MarketUpdateUserLogsManager extends StateManager[UserLogsState] {
  initWithDefaultState(UserLogsState())

  def getOrderInfos(param: QueryUserOrders): Seq[OrderInfo] = {
    def eval(orderInfo: OrderInfo) = (param.status.isEmpty || param.status.get == orderInfo.status)
    state.orderInfoMap.getOrElse(param.userId, Nil)
      .filter(eval)
      .drop(param.skipOrders.getOrElse(0)).
      take(param.numOrders.getOrElse(100))
  }

  def addOrUpdateOrderInfo(oi: OrderInfo) = {

    val userId = oi.order.userId
    val orderId = oi.order.id
    var orderInfos = state.orderInfoMap.getOrElse(userId, Nil)
    val idx = orderInfos.indexWhere(_.order.id == orderId)
    orderInfos = if (idx == -1) oi +: orderInfos else {
      val (head, tail) = orderInfos.splitAt(idx)
      head ++ (mergeOrderInfos(tail(0), oi) +: tail.drop(1))
    }
    state = state.copy(orderInfoMap = state.orderInfoMap + (userId -> orderInfos))
  }

  def cancelOrder(order: Order) = {
    val orderInfos = state.orderInfoMap.getOrElse(order.userId, Nil) map { oi =>
      if (oi.order.id == order.id) oi.copy(status = OrderStatus.Cancelled) else oi
    }
    state = state.copy(orderInfoMap = state.orderInfoMap + (order.userId -> orderInfos))
  }

  private def mergeOrderInfos(old: OrderInfo, neu: OrderInfo): OrderInfo = {
    old.copy(status = neu.status, remainingQuantity = neu.remainingQuantity, inAmount = old.inAmount + neu.inAmount)
  }
}