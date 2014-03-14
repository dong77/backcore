/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.postmarket

import org.specs2.mutable._
import scala.collection.immutable.SortedSet
import com.coinport.coinex.data._
import com.coinport.coinex.data.MarketState
import OrderStatus._
import Implicits._
import Currency._

class UserLogsManagerSpec extends Specification {

  /**
   * struct QueryUserLog{1: i64 userId, 2: optional i32 numOrders, 3: optional i32 skipOrders, 4: optional OrderStatus status, 5: optional i32 numTxs, 6: optional i32 skipTxs}
   * struct QueryUserLogResult{1: i64 userId, 2: UserLog userLog}
   */
  "UserLogsManager" should {
    "change existing order to cancelled state without changing other fields" in {
      val side = Btc ~> Rmb
      val order1 = Order(userId = 666, id = 1, price = Some(1.0 / 4500), quantity = 150, takeLimit = Some(11), timestamp = Some(123456L))
      val orderInfo1 = OrderInfo(side, order1, Pending, 50, 111)

      val order2 = Order(userId = 666, id = 2, quantity = 12)
      val orderInfo2 = OrderInfo(side.reverse, order2, PartiallyExecuted, 50, 3)

      val order3 = Order(userId = 666, id = 3, quantity = 90)
      val orderInfo3 = OrderInfo(side.reverse, order3, PartiallyExecuted, 5, 0)

      val order4 = Order(userId = 777, id = 3, quantity = 90)
      val orderInfo4 = OrderInfo(side.reverse, order4, PartiallyExecuted, 5, 0)

      val manager = new UserLogsManager()
      manager.addOrUpdateOrderInfo(orderInfo1)
      manager.getOrderInfos(QueryUserLog(666)) mustEqual UserLog(Seq(orderInfo1))

      manager.addOrUpdateOrderInfo(orderInfo2)
      manager.getOrderInfos(QueryUserLog(666)) mustEqual UserLog(Seq(orderInfo2, orderInfo1))

      manager.addOrUpdateOrderInfo(orderInfo3)
      manager.addOrUpdateOrderInfo(orderInfo4)
      manager.getOrderInfos(QueryUserLog(666)) mustEqual UserLog(Seq(orderInfo3, orderInfo2, orderInfo1))
      manager.getOrderInfos(QueryUserLog(666, numOrders = Option(2))) mustEqual UserLog(Seq(orderInfo3, orderInfo2))
      manager.getOrderInfos(QueryUserLog(666, numOrders = Option(2), skipOrders = Option(1))) mustEqual UserLog(Seq(orderInfo2, orderInfo1))
      manager.getOrderInfos(QueryUserLog(666, numOrders = Option(2), skipOrders = Option(1), status = Some(Pending))) mustEqual UserLog(Nil)
      manager.getOrderInfos(QueryUserLog(666, numOrders = Option(2), status = Some(Pending))) mustEqual UserLog(Seq(orderInfo1))

      manager.cancelOrder(order1)
      manager.getOrderInfos(QueryUserLog(666)) mustEqual UserLog(Seq(orderInfo3, orderInfo2, orderInfo1.copy(status = Cancelled)))

      manager.cancelOrder(order3)
      manager.getOrderInfos(QueryUserLog(666)) mustEqual UserLog(Seq(orderInfo3.copy(status = Cancelled), orderInfo2, orderInfo1.copy(status = Cancelled)))

      val order11 = Order(userId = 666, id = 1, quantity = 100, takeLimit = Some(12), timestamp = Some(12345698L))
      val orderInfo11 = OrderInfo(side, order11, Pending, 88, 222)
      manager.addOrUpdateOrderInfo(orderInfo11)
      manager.getOrderInfos(QueryUserLog(666)).orderInfos(2) mustEqual orderInfo1.copy(remainingQuantity = 88, inAmount = 111 + 222)
      
      manager().userLogs.size mustEqual 2
    }
  }
}