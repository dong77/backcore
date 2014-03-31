/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable._
import scala.collection.immutable.SortedSet
import com.coinport.coinex.data._
import com.coinport.coinex.data.MarketState
import OrderStatus._
import Implicits._
import Currency._

class OrderManagerSpec extends Specification {
  //  "UserOrdersManager" should {
  //    "change existing order to cancelled state without changing other fields" in {
  //      val side = Btc ~> Rmb
  //      val order1 = Order(userId = 666, id = 1, price = Some(1.0 / 4500), quantity = 150, takeLimit = Some(11), timestamp = Some(123456L))
  //      val orderInfo1 = OrderInfo(side, order1, 55, 111, Pending, Some(12))
  //
  //      val order2 = Order(userId = 666, id = 2, quantity = 12)
  //      val orderInfo2 = OrderInfo(side.reverse, order2, 50, 3, PartiallyExecuted)
  //
  //      val order3 = Order(userId = 666, id = 3, quantity = 90)
  //      val orderInfo3 = OrderInfo(side.reverse, order3, 5, 0, PartiallyExecuted)
  //
  //      val order4 = Order(userId = 777, id = 3, quantity = 90)
  //      val orderInfo4 = OrderInfo(side.reverse, order4, 5, 0, PartiallyExecuted)
  //
  //      val manager = new UserOrdersManager()
  //      manager.addOrder(orderInfo1)
  //      manager.getOrders(QueryUserOrders(666)) mustEqual Seq(orderInfo1)
  //
  //      manager.addOrder(orderInfo2)
  //      manager.getOrders(QueryUserOrders(666)) mustEqual Seq(orderInfo2, orderInfo1)
  //
  //      manager.addOrder(orderInfo3)
  //      manager.addOrder(orderInfo4)
  //
  //      manager.getOrders(QueryUserOrders(666)) mustEqual Seq(orderInfo3, orderInfo2, orderInfo1)
  //      manager.getOrders(QueryUserOrders(666, numOrders = Option(2))) mustEqual Seq(orderInfo3, orderInfo2)
  //      manager.getOrders(QueryUserOrders(666, numOrders = Option(2), skipOrders = Option(1))) mustEqual Seq(orderInfo2, orderInfo1)
  //      manager.getOrders(QueryUserOrders(666, numOrders = Option(2), skipOrders = Option(1), status = Some(Pending))) mustEqual Nil
  //      manager.getOrders(QueryUserOrders(666, numOrders = Option(2), status = Some(Pending))) mustEqual Seq(orderInfo1)
  //
  //      manager.cancelOrder(order1)
  //      manager.getOrders(QueryUserOrders(666)) mustEqual Seq(orderInfo3, orderInfo2, orderInfo1.copy(status = Cancelled))
  //
  //      manager.cancelOrder(order3)
  //      manager.getOrders(QueryUserOrders(666)) mustEqual Seq(orderInfo3.copy(status = Cancelled), orderInfo2, orderInfo1.copy(status = Cancelled))
  //
  //      val order11 = Order(userId = 666, id = 1, quantity = 100, takeLimit = Some(12), timestamp = Some(12345698L))
  //      val orderInfo11 = OrderInfo(side, order11, 88, 222, Pending, Some(456))
  //      manager.updateOrder(orderInfo11)
  //      manager.getOrders(QueryUserOrders(666))(2) mustEqual orderInfo1.copy(outAmount = 55 + 88, inAmount = 111 + 222, lastTxTimestamp = Some(456))
  //
  //      manager().orderInfoMap.size mustEqual 2
  //    }
  //  }
}