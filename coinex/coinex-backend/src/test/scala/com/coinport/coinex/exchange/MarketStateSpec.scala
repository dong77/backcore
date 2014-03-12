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

class MarketStateSpec extends Specification {
    "MarketState in exchange" should {
        "add new orders into pending order pool and replace existing ones" in {
            var m = MarketState(RMB ~> BTC)
            val side = RMB ~> BTC
            val order1 = Order(side, OrderData(1L, 100L, BUY, LIMIT, 100L, 1000L, 100000L))
            val order2 = Order(side, OrderData(1L, 101L, BUY, LIMIT, 101L, 1000L, 100001L))

            m = m.addOrder(order1)
            m = m.addOrder(order2)

            m.orderMap.size mustEqual 1
            m.orderMap(1L) mustEqual order2

            val order3 = Order(side, OrderData(1L, 103L))
            val order4 = Order(side, OrderData(1L, 104L))

            m = m.addOrder(order3)
            m = m.addOrder(order4)

            m.orderMap.size mustEqual 1
            m.orderMap(1L) mustEqual order4
            m.getOrderPool(LIMIT, BUY).size mustEqual 1
            m.getOrderPool(LIMIT, BUY).head mustEqual order4.data
        }

        "sort limit-price orders correctly" in {
            var m = MarketState(RMB ~> BTC)
            val side = RMB ~> BTC

            val order1 = Order(side, OrderData(1L, 100L, BUY, LIMIT, 100L, 10000L, 100000L))
            val order2 = Order(side, OrderData(2L, 101L, BUY, LIMIT, 100L, 9999L, 100001L))
            val order3 = Order(side, OrderData(3L, 102L, BUY, LIMIT, 100L, 10001L, 100002L))

            val order4 = Order(side, OrderData(4L, 100L, SELL, LIMIT, 100L, 10000L, 100000L))
            val order5 = Order(side, OrderData(5L, 101L, SELL, LIMIT, 100L, 9999L, 100001L))
            val order6 = Order(side, OrderData(6L, 102L, SELL, LIMIT, 100L, 10001L, 100002L))

            m = m.addOrder(order1)
            m = m.addOrder(order2)
            m = m.addOrder(order3)

            m = m.addOrder(order4)
            m = m.addOrder(order5)
            m = m.addOrder(order6)

            m.getOrderPool(LIMIT, BUY).toList mustEqual order3.data :: order1.data :: order2.data :: Nil
            m.orderMap mustEqual Map(order1.data.id -> order1, order2.data.id -> order2, order3.data.id -> order3,
                order4.data.id -> order4, order5.data.id -> order5, order6.data.id -> order6)
        }

        "keep unchanged after removing non-existing orders" in {
            var m = MarketState(RMB ~> BTC)
            val side = RMB ~> BTC
            val order1 = Order(side, OrderData(1L, 10L, BUY, LIMIT, 100L, 1000L, 100001L))
            m = m.addOrder(order1)

            m.removeOrder(2) mustEqual m
        }

        "remove existing orders if id matches" in {
            var m = MarketState(RMB ~> BTC)
            val side = RMB ~> BTC
            val order1 = Order(side, OrderData(1L, 10L, BUY, LIMIT, 100L, 1000L, 100001L))
            val order2 = Order(side, OrderData(2L, 10L, BUY, LIMIT, 100L, 1000L, 100001L))

            m.addOrder(order1).addOrder(order2).removeOrder(1).removeOrder(2) mustEqual m
        }
    }
}
