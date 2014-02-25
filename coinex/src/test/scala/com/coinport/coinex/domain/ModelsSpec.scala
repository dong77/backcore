package com.coinport.coinex.domain.models

import org.specs2.mutable._
class ModelsSpec extends Specification {
  import Market._
  "Market" should {
    "add new orders into pending order pool and replace existing ones" in {
      var m = Market(BTC, RMB)
      val side = MarketSide(BTC, RMB)
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      val order2 = Order(side, OrderData(1L, 101, 1000.0))

      m = m.addOrder(order1)
      m = m.addOrder(order2)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual OrderData(1L, 101, 1000.0)
      m.marketPriceOrderPools mustEqual EmptyOrderPools
      m.getLimitPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.getLimitPriceOrderPool(side).size mustEqual 1
      m.getLimitPriceOrderPool(side).head mustEqual order2.data

      val order3 = Order(side, OrderData(1L, 103))
      val order4 = Order(side, OrderData(1L, 104))

      m = m.addOrder(order3)
      m = m.addOrder(order4)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual OrderData(1L, 104)
      m.limitPriceOrderPools mustEqual EmptyOrderPools
      m.getMarketPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.getMarketPriceOrderPool(side).size mustEqual 1
      m.getMarketPriceOrderPool(side).head mustEqual order4.data
    }

    "sort limit-price orders correctly" in {
      var m = Market(BTC, RMB)
      val side = MarketSide(BTC, RMB)
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      val order2 = Order(side, OrderData(2L, 100, 999.99))
      val order3 = Order(side, OrderData(3L, 100, 1000.1))
      m = m.addOrder(order1)
      m = m.addOrder(order2)
      m = m.addOrder(order3)

      m.marketPriceOrderPools mustEqual EmptyOrderPools
      m.getLimitPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.getLimitPriceOrderPool(side).toList mustEqual order2.data :: order1.data :: order3.data :: Nil
      m.orderMap mustEqual Map(order1.data.id -> order1.data, order2.data.id -> order2.data, order3.data.id -> order3.data)
    }

    "sort market-price orders correctly and correct minor price" in {
      var m = Market(BTC, RMB)
      val side = MarketSide(BTC, RMB)
      val order1 = Order(side, OrderData(1L, 100, 0))
      val order2 = Order(side, OrderData(2L, 100, 0))
      val order3 = Order(side, OrderData(3L, 100, -1))
      m = m.addOrder(order1)
      m = m.addOrder(order2)
      m = m.addOrder(order3)

      m.limitPriceOrderPools mustEqual EmptyOrderPools
      m.getMarketPriceOrderPool(side.reverse) mustEqual EmptyOrderPool
      m.getMarketPriceOrderPool(side).toList mustEqual order1.data :: order2.data :: OrderData(3L, 100, 0) :: Nil
      m.orderMap mustEqual Map(order1.data.id -> order1.data, order2.data.id -> order2.data, order3.data.id -> OrderData(3L, 100, 0))
    }

    "keep unchanged after removing non-existing orders" in {
      val market = Market(BTC, RMB)
      val side = MarketSide(BTC, RMB)
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      var m = market.addOrder(order1)

      m.removeOrder(2) mustEqual m
    }

    "remove existing orders if id matches" in {
      val market = Market(BTC, RMB)
      val side = MarketSide(BTC, RMB)
      val order1 = Order(side, OrderData(1L, 100, 1000.0))
      val order2 = Order(side, OrderData(2L, 100))
      
      market.addOrder(order1).addOrder(order2).removeOrder(1).removeOrder(2) mustEqual market
    }
  }
}