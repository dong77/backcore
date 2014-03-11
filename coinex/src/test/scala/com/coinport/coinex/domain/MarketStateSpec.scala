/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.domain

import org.specs2.mutable._
import MarketState._

class MarketStateSpec extends Specification {
  val rand = new scala.util.Random
  def newMarket = if (rand.nextBoolean) MarketState(BTC ~> RMB) else MarketState(BTC <~ RMB)

  "MarketState" should {
    "add new orders into pending order pool and replace existing ones" in {
      var m = newMarket
      val side = BTC ~> RMB
      val order1 = Order(888L, 1L, 100, 1000.0)
      val order2 = Order(888L, 1L, 101, 1000.0)

      m = m.addOrder(side, order1)
      m = m.addOrder(side, order2)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual order2
      m.marketPriceOrderPools mustEqual EmptyOrderPools
      m.limitPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.limitPriceOrderPool(side).size mustEqual 1
      m.limitPriceOrderPool(side).head mustEqual order2

      val order3 = Order(888L, 1L, 103)
      val order4 = Order(888L, 1L, 104)

      m = m.addOrder(side, order3)
      m = m.addOrder(side, order4)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual order4
      m.limitPriceOrderPools mustEqual EmptyOrderPools
      m.marketPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.marketPriceOrderPool(side).size mustEqual 1
      m.marketPriceOrderPool(side).head mustEqual order4
    }

    "sort limit-price orders correctly" in {
      var m = newMarket
      val side = BTC ~> RMB
      val order1 = Order(888L, 1L, 100, 1000.0)
      val order2 = Order(888L, 2L, 100, 999.99)
      val order3 = Order(888L, 3L, 100, 1000.1)
      m = m.addOrder(side, order1)
      m = m.addOrder(side, order2)
      m = m.addOrder(side, order3)

      m.marketPriceOrderPools mustEqual EmptyOrderPools
      m.limitPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.limitPriceOrderPool(side).toList mustEqual order2 :: order1 :: order3 :: Nil
      m.orderMap mustEqual Map(order1.id -> order1, order2.id -> order2, order3.id -> order3)
    }

    "sort market-price orders correctly" in {
      var m = newMarket
      val side = BTC ~> RMB
      val order1 = Order(888L, 1L, 100, 0)
      val order2 = Order(888L, 2L, 100, 0)
      val order3 = Order(888L, 3L, 100, 0)
      m = m.addOrder(side, order1)
      m = m.addOrder(side, order2)
      m = m.addOrder(side, order3)

      m.limitPriceOrderPools mustEqual EmptyOrderPools
      m.marketPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.marketPriceOrderPool(side).toList mustEqual order1 :: order2 :: Order(888L, 3L, 100, 0) :: Nil
      m.orderMap mustEqual Map(order1.id -> order1, order2.id -> order2, order3.id -> Order(888L, 3L, 100, 0))
    }

    "keep unchanged after removing non-existing orders" in {
      val market = newMarket
      val side = BTC ~> RMB
      val order1 = Order(888L, 1L, 100, 1000.0)
      var m = market.addOrder(side, order1)

      m.removeOrder(2) mustEqual m
    }

    "remove existing orders if id matches" in {
      val market = newMarket
      val side = BTC ~> RMB
      val order1 = Order(888L, 1L, 100, 1000.0)
      val order2 = Order(888L, 2L, 100)

      market.addOrder(side, order1).addOrder(side, order2).removeOrder(1).removeOrder(2) mustEqual market
    }
  }
}