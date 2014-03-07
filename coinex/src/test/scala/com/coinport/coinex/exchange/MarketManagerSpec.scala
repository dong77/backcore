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
        quantity = 100L, timestamp = 100001L)
      val maker1 = Order(side, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2L, uid = 20L, BUY, LIMIT, price = 4000L,
        quantity = 500L, timestamp = 100002L)
      val maker2 = Order(side, makerData2)
      val txs = mm.addOrder(maker2)

      mm().orderMap mustEqual Map(1L -> maker1, 2 -> maker2)
      implicit val comparator = getComparator(BUY)
      mm().getOrderPool(LIMIT, BUY) mustEqual SortedSet(makerData1, makerData2)

      txs mustEqual Nil
    }

    "normal exchange" in {
      val mm = new MarketManager(RMB ~> BTC)
      var order = Order(side, OrderData(id = 1L, uid = 10L, SELL, LIMIT, price = 4000L,
        quantity = 100L, timestamp = 100001L))
      mm.addOrder(order)
      order = Order(side, OrderData(id = 2L, uid = 20L, SELL, LIMIT, price = 5000L,
        quantity = 100L, timestamp = 100002L))
      var txs = mm.addOrder(order)
      txs mustEqual Nil

      order = Order(side, OrderData(id = 3L, uid = 30L, BUY, LIMIT, price = 7000L,
        quantity = 340L, timestamp = 100003L))
      txs = mm.addOrder(order)

      txs match {
        case List(first, second) =>
          first.copy(timestamp = 0) mustEqual
            Transaction(1,Transfer(20,30,BTC,100),Transfer(30,20,RMB,500000),5000,3,2,0)
          second.copy(timestamp = 0) mustEqual
            Transaction(0,Transfer(10,30,BTC,100),Transfer(30,10,RMB,400000),4000,3,1,0)
      }

      order = Order(side, OrderData(id = 4L, uid = 40L, BUY, LIMIT, price = 7000L,
        quantity = 10L, timestamp = 100004L))
      txs = mm.addOrder(order)
      txs mustEqual Nil

      order = Order(side, OrderData(id = 5L, uid = 41L, SELL, LIMIT, price = 6900L,
        quantity = 1L, timestamp = 100005L))
      txs = mm.addOrder(order)
      txs.headOption match {
        case Some(t) =>
          t.copy(timestamp = 0) mustEqual Transaction(2,Transfer(41,30,BTC,1),Transfer(30,41,RMB,7000),7000,3,5,0)
        case _ =>
          throw new Exception()
      }

      mm().getOrderPool(LIMIT, SELL).size mustEqual 0
      mm().getOrderPool(LIMIT, BUY).size mustEqual 2

      order = Order(side, OrderData(id = 6L, uid = 42L, SELL, LIMIT, price = 6900L,
        quantity = 500L, timestamp = 100006L))
      txs = mm.addOrder(order)
      txs match {
        case List(first, second) =>
          first.copy(timestamp = 0) mustEqual
            Transaction(4,Transfer(42,40,BTC,10),Transfer(40,42,RMB,70000),7000,4,6,0)
          second.copy(timestamp = 0) mustEqual
            Transaction(3,Transfer(42,30,BTC,139),Transfer(30,42,RMB,973000),7000,3,6,0)
      }
      mm().getOrderPool(LIMIT, BUY).size mustEqual 0
      mm().getOrderPool(LIMIT, SELL).size mustEqual 1
      1 mustEqual 1
    }
  }
}
