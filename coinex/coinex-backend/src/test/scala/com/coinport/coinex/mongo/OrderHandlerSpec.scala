/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.mongo

import com.coinport.coinex.data.Currency.{ Btc, Rmb }
import org.specs2.mutable._
import com.coinport.coinex.common.EmbeddedMongoSupport
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.ot.OrderMongoHandler

class OrderHandlerSpec extends Specification with EmbeddedMongoSupport {
  class OrderClass extends OrderMongoHandler {
    val coll = database("OrderHandlerSpec")
  }

  val market = Btc ~> Rmb
  step(embeddedMongoStartup())

  "OrderDataStateSpec" should {
    val orderClass = new OrderClass()
    "can save update OrderInfo and can sort count data" in {
      orderClass.coll.size mustEqual 0

      var orderInfos = (0 to 3).map(i => OrderInfo(market, Order(i, i, i), 10, 10, OrderStatus.Pending, None))
      orderInfos.foreach(oi => orderClass.addItem(oi))

      orderClass.coll.size mustEqual 4

      orderClass.coll.drop()
      orderInfos = (0 to 3).map(i => OrderInfo(market, Order(i, i, i), 10, 10, OrderStatus.Pending, None))
      orderInfos.foreach(oi => orderClass.addItem(oi))

      orderClass.updateItem(1, 10, 20, 1, market.reverse, 20)

      var q = QueryOrder(oid = Some(1L), cursor = Cursor(0, 2), getCount = false)

      val order_info = orderClass.getItems(q)(0)
      order_info.side mustEqual market.reverse
      order_info.order.userId mustEqual 1
      order_info.order.id mustEqual 1
      order_info.inAmount mustEqual 10
      order_info.outAmount mustEqual 20
      order_info.status.getValue() mustEqual 1
      order_info.lastTxTimestamp mustEqual Some(20)

      orderInfos = (0 to 10).map(i => OrderInfo(market, Order(i % 3, i, i), 10, 10, OrderStatus.Pending, None))
      orderInfos.foreach(oi => orderClass.addItem(oi))

      q = QueryOrder(uid = Some(1L), cursor = Cursor(0, 10), getCount = true)
      orderClass.getItems(q) mustEqual Nil
      orderClass.countItems(q) mustEqual 4

      q = QueryOrder(uid = Some(1L), cursor = Cursor(0, 10), getCount = false)
      orderClass.countItems(q) mustEqual 0
      orderClass.getItems(q).map(_.order.id) mustEqual Seq(10, 7, 4, 1)
    }
  }

  step(embeddedMongoShutdown())
}
