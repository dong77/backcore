/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.userlogs
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.StateManager

// TODO(d): make this an empty processor and make the current logic into a view.
class UserLogsProcessor extends ExtendedProcessor {
  override def processorId = "coinex_ulp"
  val manager = new UserLogsStateManager

  def receiveMessage: Receive = {
    case DebugDump => log.info("state: {}", manager())

    case OrderCancelled(side, order) =>
      manager.markOrderAs(order, OrderStatus.Cancelled)

    case MarketUpdate(oi, amount, fullyExecutedOrders, partiallyExecutedOrders, txs) =>
      val status =
        if (amount == 0) OrderStatus.FullyExecuted
        else if (amount == oi.order.quantity) OrderStatus.Pending
        else OrderStatus.PartiallyExecuted

      manager.addOrderAs(oi.side, oi.order, status)
      fullyExecutedOrders foreach { manager.markOrderAs(_, OrderStatus.FullyExecuted) }
      partiallyExecutedOrders foreach { manager.markOrderAs(_, OrderStatus.PartiallyExecuted) }

    case q: QueryUserLog =>
      val userLog = manager.getOrderInfos(q)
      sender ! QueryUserLogResult(q.userId, userLog)
  }
}

class UserLogsStateManager extends StateManager[UserLogs] {
  initWithDefaultState(UserLogs())

  def markOrderAs(order: Order, status: OrderStatus) = {
    var userLog = state.userLogs.getOrElse(order.userId, UserLog(Nil, Nil))
    val orderInfos = userLog.orderInfos map { oi =>
      if (oi.order.id == order.id) oi.copy(status = status)
      else oi
    }
    userLog = userLog.copy(orderInfos = orderInfos)
    val userLogs = state.userLogs + (order.userId -> userLog)
    state = state.copy(userLogs = userLogs)
  }

  def addOrderAs(side: MarketSide, order: Order, status: OrderStatus) = {
    var userLog = state.userLogs.getOrElse(order.userId, UserLog(Nil, Nil))
    val orderInfos = Seq(OrderInfo(side, order, status)) ++ userLog.orderInfos
    userLog = userLog.copy(orderInfos = orderInfos)
    val userLogs = state.userLogs + (order.userId -> userLog)
    state = state.copy(userLogs = userLogs)
  }

  def getOrderInfos(param: QueryUserLog): UserLog = {
    def eval(orderInfo: OrderInfo) = (param.status.isEmpty || param.status.get == orderInfo.status)
    val userLog = state.userLogs.getOrElse(param.userId, UserLog(Nil, Nil))
    val orderInfos = userLog.orderInfos.filter(eval).drop(param.skipOrders.getOrElse(0)).take(param.numOrders.getOrElse(100))
    val txs = userLog.txs.drop(param.skipTxs.getOrElse(0)).take(param.numTxs.getOrElse(100))
    UserLog(orderInfos, txs)
  }
}