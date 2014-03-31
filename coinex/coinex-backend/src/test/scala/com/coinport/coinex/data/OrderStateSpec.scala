/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.data

import com.coinport.coinex.data.Currency.{ Btc, Rmb }
import org.specs2.mutable.Specification
import Implicits._

class OrderStateSpec extends Specification {
  //  "OrderDataStateSpec" should {
  //    "add item into state and get them all" in {
  //      var state = OrderDataState()
  //
  //      val market = Btc ~> Rmb
  //
  //      val orderInfos = (0 to 3).map(i => OrderInfo(market, Order(i, i, i), 10, 10, OrderStatus.Pending, None))
  //      orderInfos.foreach(oi => state = state.addItem(oi.order.id, oi))
  //
  //      (0 to 3) foreach (i => state.getItem(Some(i), i, i) mustEqual Seq(orderInfos(i)))
  //
  //      var updateOrder = orderInfos.apply(2).copy(outAmount = 100, inAmount = 100)
  //      state = state.updateItem(2, updateOrder)
  //      state.getItem(Some(2), 0, 0) mustEqual Seq(updateOrder.copy(outAmount = 100 + 10, inAmount = 100 + 10))
  //
  //      updateOrder = orderInfos.apply(2).copy(outAmount = 100, inAmount = 100, side = Rmb ~> Btc)
  //      state = state.updateItem(2, updateOrder)
  //      state.getItem(Some(2), 0, 0) mustNotEqual Seq(updateOrder.copy(outAmount = 100 + 10, inAmount = 100 + 10))
  //
  //      state = state.cancelItem(1)
  //      state.getItem(Some(1), 0, 0)(0).status mustEqual OrderStatus.Cancelled
  //    }
  //  }
}
