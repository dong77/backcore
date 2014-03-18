/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.exchange

import org.specs2.mutable._
import scala.collection.immutable.SortedSet
import BuyOrSell._
import MarketOrLimit._
import MarketState._

class MarketManagerSpec extends Specification {
  val side = RMB ~> BTC
  "MarketManager in exchange" should {
    "allow multiple market-price orders to co-exist" in {
      val mm = new MarketManager(RMB ~> BTC)

      val makerData1 = OrderData(id = 1L, uid = 10L, BUY, LIMIT, price = 4000L,
        quantity = 100L, amount = -1L, timestamp = 100001L)
      val maker1 = Order(side, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2L, uid = 20L, BUY, LIMIT, price = 4000L,
        quantity = 500L, amount = -1L, timestamp = 100002L)
      val maker2 = Order(side, makerData2)
      val txs = mm.addOrder(maker2)

      mm().orderMap mustEqual Map(1L -> maker1, 2 -> maker2)
      implicit val comparator = getComparator(BUY)
      mm().getOrderPool(LIMIT, BUY) mustEqual SortedSet(makerData1, makerData2)

      txs mustEqual Nil
    }

    "limit order" in {
      val mm = new MarketManager(RMB ~> BTC)
      var order = Order(side, OrderData(id = 1L, uid = 10L, SELL, LIMIT, price = 4000L,
        quantity = 100L, amount = -1L, timestamp = 100001L))
      mm.addOrder(order)
      order = Order(side, OrderData(id = 2L, uid = 20L, SELL, LIMIT, price = 5000L,
        quantity = 100L, amount = -1L, timestamp = 100002L))
      var txs = mm.addOrder(order)
      txs mustEqual Nil

      order = Order(side, OrderData(id = 3L, uid = 30L, BUY, LIMIT, price = 7000L,
        quantity = 340L, amount = -1L, timestamp = 100003L))
      txs = mm.addOrder(order)

      txs match {
        case List(first, second) =>
          first.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(20, 30, BTC, 100), Transfer(30, 20, RMB, 500000), 5000, 3, 2, 0)
          second.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(10, 30, BTC, 100), Transfer(30, 10, RMB, 400000), 4000, 3, 1, 0)
      }

      order = Order(side, OrderData(id = 4L, uid = 40L, BUY, LIMIT, price = 7000L,
        quantity = 10L, amount = -1L, timestamp = 100004L))
      txs = mm.addOrder(order)
      txs mustEqual Nil

      order = Order(side, OrderData(id = 5L, uid = 41L, SELL, LIMIT, price = 6900L,
        quantity = 1L, amount = -1L, timestamp = 100005L))
      txs = mm.addOrder(order)
      txs.headOption match {
        case Some(t) =>
          t.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(41, 30, BTC, 1), Transfer(30, 41, RMB, 7000), 7000, 3, 5, 0)
        case _ =>
          throw new Exception()
      }

      mm().getOrderPool(LIMIT, SELL).size mustEqual 0
      mm().getOrderPool(LIMIT, BUY).size mustEqual 2

      order = Order(side, OrderData(id = 6L, uid = 42L, SELL, LIMIT, price = 6900L,
        quantity = 500L, amount = -1L, timestamp = 100006L))
      txs = mm.addOrder(order)
      txs match {
        case List(first, second) =>
          first.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(42, 40, BTC, 10), Transfer(40, 42, RMB, 70000), 7000, 4, 6, 0)
          second.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(42, 30, BTC, 139), Transfer(30, 42, RMB, 973000), 7000, 3, 6, 0)
      }
      mm().getOrderPool(LIMIT, BUY).size mustEqual 0
      mm().getOrderPool(LIMIT, SELL).size mustEqual 1
    }

    "sell market order" in {
      val mm = new MarketManager(RMB ~> BTC)
      var order = Order(side, OrderData(id = 1L, uid = 10L, BUY, LIMIT, price = 4000L,
        quantity = 100L, amount = -1L, timestamp = 100001L))
      mm.addOrder(order)
      order = Order(side, OrderData(id = 2L, uid = 20L, BUY, LIMIT, price = 5000L,
        quantity = 100L, amount = -1L, timestamp = 100002L))
      var txs = mm.addOrder(order)
      txs mustEqual Nil
      order = Order(side, OrderData(id = 3L, uid = 30L, BUY, LIMIT, price = 7000L,
        quantity = 340L, amount = -1L, timestamp = 100003L))
      txs = mm.addOrder(order)
      txs mustEqual Nil
      order = Order(side, OrderData(id = 4L, uid = 40L, BUY, LIMIT, price = 7000L,
        quantity = 10L, amount = -1L, timestamp = 100004L))
      txs = mm.addOrder(order)
      txs mustEqual Nil

      mm().getOrderPool(LIMIT, BUY).size mustEqual 4

      order = Order(side, OrderData(id = 5L, uid = 41L, SELL, MARKET, price = 0,
        quantity = 1000L, amount = -1L, timestamp = 100005L))
      txs = mm.addOrder(order)
      txs match {
        case List(first, second, third, forth) =>
          first.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(41, 10, BTC, 100), Transfer(10, 41, RMB, 400000), 4000, 1, 5, 0)
          second.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(41, 20, BTC, 100), Transfer(20, 41, RMB, 500000), 5000, 2, 5, 0)
          third.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(41, 40, BTC, 10), Transfer(40, 41, RMB, 70000), 7000, 4, 5, 0)
          forth.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(41, 30, BTC, 340), Transfer(30, 41, RMB, 2380000), 7000, 3, 5, 0)
      }
      mm().getOrderPool(LIMIT, BUY).size mustEqual 0
      mm().getOrderPool(LIMIT, SELL).size mustEqual 0
      mm().getOrderPool(MARKET, BUY).size mustEqual 0
      mm().getOrderPool(MARKET, SELL).size mustEqual 1

      mm().getOrderPool(MARKET, SELL).headOption match {
        case Some(orderData) => orderData mustEqual OrderData(5, 41, SELL, MARKET, 450, 0, -1, 100005)
        case _ =>
          throw new Exception()
      }

      // buy market order could co-exist with sell market order
      order = Order(side, OrderData(id = 7L, uid = 43L, BUY, MARKET, price = -1,
        quantity = 500L, amount = 1000L, timestamp = 100007L))
      txs = mm.addOrder(order)
      txs mustEqual Nil
      mm().getOrderPool(MARKET, BUY).size mustEqual 1
      mm().getOrderPool(MARKET, SELL).size mustEqual 1

      txs = mm.addOrder(order)
      order = Order(side, OrderData(id = 6L, uid = 42L, BUY, LIMIT, price = 12345,
        quantity = 500L, amount = -1L, timestamp = 100006L))
      txs = mm.addOrder(order)
      txs match {
        case List(t) =>
          t.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(41, 42, BTC, 450), Transfer(42, 41, RMB, 5555250), 12345, 6, 5, 0)
      }
      mm().getOrderPool(LIMIT, BUY).size mustEqual 1
      mm().getOrderPool(LIMIT, SELL).size mustEqual 0
      mm().getOrderPool(MARKET, BUY).size mustEqual 1
      mm().getOrderPool(MARKET, SELL).size mustEqual 0

      mm().getOrderPool(LIMIT, BUY).headOption match {
        case Some(orderData) => orderData mustEqual OrderData(6, 42, BUY, LIMIT, 50, 12345, -1, 100006)
        case _ =>
          throw new Exception()
      }
    }
    "buy market order" in {
      val mm = new MarketManager(RMB ~> BTC)
      var order = Order(side, OrderData(id = 1L, uid = 10L, SELL, LIMIT, price = 4000L,
        quantity = 100L, amount = -1L, timestamp = 100001L))
      mm.addOrder(order)
      order = Order(side, OrderData(id = 2L, uid = 20L, SELL, LIMIT, price = 5000L,
        quantity = 100L, amount = -1L, timestamp = 100002L))
      var txs = mm.addOrder(order)
      txs mustEqual Nil
      order = Order(side, OrderData(id = 3L, uid = 30L, SELL, LIMIT, price = 7000L,
        quantity = 340L, amount = -1L, timestamp = 100003L))
      txs = mm.addOrder(order)
      txs mustEqual Nil
      order = Order(side, OrderData(id = 4L, uid = 40L, SELL, LIMIT, price = 7000L,
        quantity = 10L, amount = -1L, timestamp = 100004L))
      txs = mm.addOrder(order)
      txs mustEqual Nil

      mm().getOrderPool(LIMIT, SELL).size mustEqual 4

      order = Order(side, OrderData(id = 5L, uid = 41L, BUY, MARKET, price = 0,
        quantity = 0L, amount = 500000L, timestamp = 100005L))
      txs = mm.addOrder(order)
      txs match {
        case List(first, second) =>
          first.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(20, 41, BTC, 20), Transfer(41, 20, RMB, 100000), 5000, 5, 2, 0)
          second.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(10, 41, BTC, 100), Transfer(41, 10, RMB, 400000), 4000, 5, 1, 0)
      }
      mm().getOrderPool(MARKET, BUY).size mustEqual 0
      mm().getOrderPool(MARKET, SELL).size mustEqual 0
      mm().getOrderPool(LIMIT, BUY).size mustEqual 0
      mm().getOrderPool(LIMIT, SELL).size mustEqual 3

      order = Order(side, OrderData(id = 6L, uid = 41L, BUY, MARKET, price = 0,
        quantity = 0L, amount = 5000000L, timestamp = 100006L))
      txs = mm.addOrder(order)
      txs match {
        case List(first, second, third) =>
          first.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(40, 41, BTC, 10), Transfer(41, 40, RMB, 70000), 7000, 6, 4, 0)
          second.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(30, 41, BTC, 340), Transfer(41, 30, RMB, 2380000), 7000, 6, 3, 0)
          third.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(20, 41, BTC, 80), Transfer(41, 20, RMB, 400000), 5000, 6, 2, 0)
      }
      mm().getOrderPool(MARKET, BUY).size mustEqual 1
      mm().getOrderPool(MARKET, SELL).size mustEqual 0
      mm().getOrderPool(LIMIT, BUY).size mustEqual 0
      mm().getOrderPool(LIMIT, SELL).size mustEqual 0

      order = Order(side, OrderData(id = 7L, uid = 42L, SELL, LIMIT, price = 100,
        quantity = 10L, amount = -1, timestamp = 100007L))
      txs = mm.addOrder(order)
      txs match {
        case List(t) =>
          t.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(42, 41, BTC, 10), Transfer(41, 42, RMB, 1000), 100, 6, 7, 0)
      }

      mm().getOrderPool(MARKET, BUY).headOption match {
        case Some(orderData) => orderData mustEqual
          OrderData(6, 41, BUY, MARKET, 21490, 9223372036854775807L, 2149000, 100006)
        case _ =>
          throw new Exception()
      }
      order = Order(side, OrderData(id = 8L, uid = 43L, SELL, LIMIT, price = 100,
        quantity = 1000000L, amount = -1, timestamp = 100008L))
      txs = mm.addOrder(order)
      txs match {
        case List(t) =>
          t.copy(id = 0, timestamp = 0) mustEqual
            Transaction(0, Transfer(43, 41, BTC, 21490), Transfer(41, 43, RMB, 2149000), 100, 6, 8, 0)
      }
      mm().getOrderPool(LIMIT, SELL).headOption match {
        case Some(orderData) => orderData mustEqual
          OrderData(8, 43, SELL, LIMIT, 978510, 100, -1, 100008)
        case _ =>
          throw new Exception()
      }
    }
  }
}
