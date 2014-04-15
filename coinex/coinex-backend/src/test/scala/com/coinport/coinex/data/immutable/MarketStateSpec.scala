/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.data.immutable

import org.specs2.mutable._

import com.coinport.coinex.data._
import MarketState._
import Implicits._
import Currency._

class MarketStateSpec extends Specification {
  val rand = new scala.util.Random
  def newMarket = if (rand.nextBoolean) MarketState(Btc ~> Rmb) else MarketState(Btc <~ Rmb)

  "MarketState" should {
    "add new orders into pending order pool and replace existing ones" in {
      var m = newMarket
      val side = Btc ~> Rmb
      val order1 = Order(888L, 1L, 100, Some(1000.0))
      val order2 = Order(888L, 1L, 101, Some(1000.0))

      m = m.addOrder(side, order1)
      m = m.addOrder(side, order2)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual order2
      m.orderPool(side.reverse) mustEqual EmptyOrderPool
      m.orderPool(side).size mustEqual 1
      m.orderPool(side).head mustEqual order2

      val order3 = Order(888L, 1L, 103, None)
      val order4 = Order(888L, 1L, 104, None)

      m = m.addOrder(side, order3)
      m = m.addOrder(side, order4)

      m.orderMap.size mustEqual 1
      m.orderMap(1L) mustEqual order2
    }

    "sort limit-price orders correctly" in {
      var m = newMarket
      val side = Btc ~> Rmb
      val order1 = Order(888L, 1L, 100, Some(1000.0))
      val order2 = Order(888L, 2L, 100, Some(999.99))
      val order3 = Order(888L, 3L, 100, Some(1000.1))
      m = m.addOrder(side, order1)
      m = m.addOrder(side, order2)
      m = m.addOrder(side, order3)

      m.orderPool(side.reverse) mustEqual EmptyOrderPool
      m.orderPool(side).toList mustEqual order2 :: order1 :: order3 :: Nil
      m.orderMap mustEqual Map(order1.id -> order1, order2.id -> order2, order3.id -> order3)
    }

    "sort market-price orders correctly" in {
      var m = newMarket
      val side = Btc ~> Rmb
      val order1 = Order(888L, 1L, 100, None)
      val order2 = Order(888L, 2L, 100, None)
      val order3 = Order(888L, 3L, 100, None)
      m = m.addOrder(side, order1)
      m = m.addOrder(side, order2)
      m = m.addOrder(side, order3)

      m.orderPools mustEqual EmptyOrderPools
      m.orderMap mustEqual Map()
    }

    "keep unchanged after removing non-existing orders" in {
      val market = newMarket
      val side = Btc ~> Rmb
      val order1 = Order(888L, 1L, 100, Some(1000.0))
      var m = market.addOrder(side, order1)

      m.removeOrder(side, 2) mustEqual m
    }

    "remove existing orders if id matches" in {
      val market = newMarket
      val side = Btc ~> Rmb
      val order1 = Order(888L, 1L, 100, Some(1000.0))
      val order2 = Order(888L, 2L, 100, None)

      var m = market.addOrder(side, order1)
      m = m.removeOrder(side.reverse, 1)
      m.orderMap.size mustEqual 1

      m = m.addOrder(side, order1)
      m = m.addOrder(side, order2)
      m = m.removeOrder(side, 1)
      m = m.removeOrder(side, 2)
      m mustEqual market
    }

    "popOrder test" in {
      val market = newMarket
      val side = Btc ~> Rmb
      val order1 = Order(888L, 1L, 100, Some(1000.0))
      val order2 = Order(888L, 2L, 100, Some(981.3))
      val m1 = market.addOrder(side, order1)
      val m2 = m1.addOrder(side, order2)
      val (headOrder, updatedMarket) = m2.popOrder(side)
      headOrder foreach { o =>
        o mustEqual order2
      }
      updatedMarket mustEqual m1
    }
  }
}
