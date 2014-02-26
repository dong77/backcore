/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.domain

import org.specs2.mutable._
import Market._

class ModelsSpec extends Specification {
  val rand = new scala.util.Random
  def newMarket = if (rand.nextBoolean) Market(BTC ~ RMB) else Market(RMB ~ BTC)

  "Market" should {
    "add new orders into pending order pool and replace existing ones" in {
      var m = newMarket
      val side = BTC ~ RMB
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      val order2 = Order(side, OrderData(1L, 101, 1000.0))

      m = m.addOrder(order1)
      m = m.addOrder(order2)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual order2
      m.marketPriceOrderPools mustEqual EmptyOrderPools
      m.limitPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.limitPriceOrderPool(side).size mustEqual 1
      m.limitPriceOrderPool(side).head mustEqual order2.data

      val order3 = Order(side, OrderData(1L, 103))
      val order4 = Order(side, OrderData(1L, 104))

      m = m.addOrder(order3)
      m = m.addOrder(order4)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual order4
      m.limitPriceOrderPools mustEqual EmptyOrderPools
      m.marketPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.marketPriceOrderPool(side).size mustEqual 1
      m.marketPriceOrderPool(side).head mustEqual order4.data
    }

    "sort limit-price orders correctly" in {
      var m = newMarket
      val side = BTC ~ RMB
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      val order2 = Order(side, OrderData(2L, 100, 999.99))
      val order3 = Order(side, OrderData(3L, 100, 1000.1))
      m = m.addOrder(order1)
      m = m.addOrder(order2)
      m = m.addOrder(order3)

      m.marketPriceOrderPools mustEqual EmptyOrderPools
      m.limitPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.limitPriceOrderPool(side).toList mustEqual order2.data :: order1.data :: order3.data :: Nil
      m.orderMap mustEqual Map(order1.data.id -> order1, order2.data.id -> order2, order3.data.id -> order3)
    }

    "sort market-price orders correctly and correct minor price" in {
      var m = newMarket
      val side = BTC ~ RMB
      val order1 = Order(side, OrderData(1L, 100, 0))
      val order2 = Order(side, OrderData(2L, 100, 0))
      val order3 = Order(side, OrderData(3L, 100, -1))
      m = m.addOrder(order1)
      m = m.addOrder(order2)
      m = m.addOrder(order3)

      m.limitPriceOrderPools mustEqual EmptyOrderPools
      m.marketPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.marketPriceOrderPool(side).toList mustEqual order1.data :: order2.data :: OrderData(3L, 100, 0) :: Nil
      m.orderMap mustEqual Map(order1.data.id -> order1, order2.data.id -> order2, order3.data.id -> order3.copy(data = OrderData(3L, 100, 0)))
    }

    "keep unchanged after removing non-existing orders" in {
      val market = newMarket
      val side = BTC ~ RMB
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      var m = market.addOrder(order1)

      m.removeOrder(2) mustEqual m
    }

    "remove existing orders if id matches" in {
      val market = newMarket
      val side = BTC ~ RMB
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      val order2 = Order(side, OrderData(2L, 100))

      market.addOrder(order1).addOrder(order2).removeOrder(1).removeOrder(2) mustEqual market
    }
  }
}