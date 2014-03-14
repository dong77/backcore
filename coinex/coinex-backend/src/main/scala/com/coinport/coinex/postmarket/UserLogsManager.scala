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

// TODO(d): test this.
private[postmarket] class UserLogsManager extends StateManager[UserLogsState] {
  initWithDefaultState(UserLogsState())

  def getOrderInfos(param: QueryUserLog): UserLog = {
    def eval(orderInfo: OrderInfo) = (param.status.isEmpty || param.status.get == orderInfo.status)
    val userLog = state.userLogs.getOrElse(param.userId, UserLog(Nil))
    val orderInfos = userLog.orderInfos.filter(eval).drop(param.skipOrders.getOrElse(0)).take(param.numOrders.getOrElse(100))
    UserLog(orderInfos)
  }

  def addOrUpdateOrderInfo(oi: OrderInfo) = {
    def merge(old: OrderInfo, neu: OrderInfo): OrderInfo = {
      old.copy(status = neu.status, remainingQuantity = neu.remainingQuantity, inAmount = old.inAmount + neu.inAmount)
    }
    val userId = oi.order.userId
    val orderId = oi.order.id
    var userLog = state.userLogs.getOrElse(userId, UserLog(Nil))
    var orderInfos = userLog.orderInfos
    val idx = orderInfos.indexWhere(_.order.id == orderId)
    orderInfos = if (idx == -1) oi +: orderInfos else {
      val (head, tail) = orderInfos.splitAt(idx)
      head ++ (merge(tail(0), oi) +: tail.drop(1))
    }
    userLog = userLog.copy(orderInfos = orderInfos)
    state = state.copy(userLogs = state.userLogs + (userId -> userLog))
  }

  def cancelOrder(order: Order) = {
    var userLog = state.userLogs.getOrElse(order.userId, UserLog(Nil))
    var orderInfos = userLog.orderInfos map { oi =>
      if (oi.order.id == order.id) oi.copy(status = OrderStatus.Cancelled) else oi
    }
    userLog = userLog.copy(orderInfos = orderInfos)
    state = state.copy(userLogs = state.userLogs + (order.userId -> userLog))
  }
}