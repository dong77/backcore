/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.domain

import org.specs2.mutable._
import scala.collection.immutable.SortedSet
import MarketState._

class MarketManagerSpec extends Specification {

  val takerSide = BTC ~> RMB
  val makerSide = takerSide.reverse

  "MarketManager" should {
    "allow multiple market-price orders to co-exist" in {
      val mm = new MarketManager(BTC ~> RMB)

      val maker1 = Order(userId = 888L, id = 1, price = 0, quantity = 100)
      mm.addOrder(makerSide, maker1)

      val maker2 = Order(userId = 888L, id = 2, price = 0, quantity = 500)
      val txs = mm.addOrder(makerSide, maker2)

      mm().orderMap mustEqual Map(1L -> maker1, 2 -> maker2)
      mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      mm().marketPriceOrderPool(makerSide) mustEqual SortedSet(maker1, maker2)
      mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual Nil
    }

    "NOT match new market-price taker order with existing market-price maker oders" in {
      val mm = new MarketManager(BTC ~> RMB)
      val maker = Order(userId = 888L, id = 1, price = 0, quantity = 100)
      mm.addOrder(makerSide, maker)

      val taker = Order(userId = 888L, id = 2, price = 0, quantity = 100)
      val txs = mm.addOrder(takerSide, taker)

      mm().orderMap mustEqual Map(1L -> maker, 2 -> taker)
      mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      mm().marketPriceOrderPool(makerSide) mustEqual SortedSet(maker)
      mm().marketPriceOrderPool(takerSide) mustEqual SortedSet(taker)

      txs mustEqual Nil
    }

    "match new market-price taker order against existing limit-price maker orders and fully execute both orders " +
      "if quantity equals" in {
        val mm = new MarketManager(BTC ~> RMB)
        val maker = Order(userId = 888L, id = 1, price = 1, quantity = 100)
        mm.addOrder(makerSide, maker)

        val taker = Order(userId = 888L, id = 2, price = 0, quantity = 100)
        val txs = mm.addOrder(takerSide, taker)

        mm().orderMap.size mustEqual 0
        mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        txs mustEqual
          Transaction(Transfer(2, BTC, 100, true), Transfer(1, RMB, 100, true)) ::
          Nil
      }

    "match new market-price taker order against existing limit-price maker orders and fully execute taker orders " +
      "if its quantity is smaller" in {
        val mm = new MarketManager(BTC ~> RMB)
        val maker = Order(userId = 888L, id = 1, price = 1, quantity = 100)
        mm.addOrder(makerSide, maker)

        val taker = Order(userId = 888L, id = 2, price = 0, quantity = 10)
        val txs = mm.addOrder(takerSide, taker)

        mm().orderMap mustEqual Map(1 -> maker.copy(quantity = 90))
        mm().limitPriceOrderPool(makerSide) mustEqual SortedSet(maker.copy(quantity = 90))
        mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        txs mustEqual
          Transaction(Transfer(2, BTC, 10, true), Transfer(1, RMB, 10, false)) ::
          Nil
      }

    "match new market-price taker order against existing limit-price maker orders and fully execute maker orders " +
      "if its quantity is smaller" in {
        val mm = new MarketManager(BTC ~> RMB)
        val maker = Order(userId = 888L, id = 1, price = 1, quantity = 10)
        mm.addOrder(makerSide, maker)

        val taker = Order(userId = 888L, id = 2, price = 0, quantity = 100)
        val txs = mm.addOrder(takerSide, taker)

        mm().orderMap mustEqual Map(2 -> taker.copy(quantity = 90))

        mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().marketPriceOrderPool(takerSide) mustEqual SortedSet(taker.copy(quantity = 90))

        mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        txs mustEqual
          Transaction(Transfer(2, BTC, 10, false), Transfer(1, RMB, 10, true)) ::
          Nil
      }

    "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
      "taker order if its quantity is smaller" in {
        val mm = new MarketManager(BTC ~> RMB)
        val maker1 = Order(userId = 888L, id = 1, price = 1, quantity = 100) // lower price
        mm.addOrder(makerSide, maker1)

        val maker2 = Order(userId = 888L, id = 2, price = 0.5, quantity = 100) // higher price
        mm.addOrder(makerSide, maker2)

        val taker = Order(userId = 888L, id = 10, price = 0, quantity = 120)
        val txs = mm.addOrder(takerSide, taker)

        mm().orderMap mustEqual Map(1 -> maker1.copy(quantity = 30)) //  100 x 0.5 + 100 x 1 - 120 = 30
        mm().limitPriceOrderPool(makerSide) mustEqual SortedSet(maker1.copy(quantity = 30))
        mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        txs mustEqual
          Transaction(Transfer(10, BTC, 70, true), Transfer(1, RMB, 70, false)) ::
          Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
          Nil
      }

    "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
      "all maker orders if their combined quantity is smaller" in {
        val mm = new MarketManager(BTC ~> RMB)
        val maker1 = Order(userId = 888L, id = 1, price = 1, quantity = 20) // lower price
        mm.addOrder(makerSide, maker1)

        val maker2 = Order(userId = 888L, id = 2, price = 0.5, quantity = 100) // higher price
        mm.addOrder(makerSide, maker2)

        val taker = Order(userId = 888L, id = 10, price = 0, quantity = 120)
        val txs = mm.addOrder(takerSide, taker)

        mm().orderMap mustEqual Map(10 -> taker.copy(quantity = 50)) //  120 - 100 x 0.5 + 20 x 1 - 120 = 50
        mm().marketPriceOrderPool(takerSide) mustEqual SortedSet(taker.copy(quantity = 50))
        mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

        mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        txs mustEqual
          Transaction(Transfer(10, BTC, 20, false), Transfer(1, RMB, 20, true)) ::
          Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
          Nil
      }
  }

  "MarketManager" should {
    "match new limit-price taker order against the highest limit-price maker order" in {
      val mm = new MarketManager(BTC ~> RMB)

      val maker1 = Order(userId = 888L, id = 1, price = 1, quantity = 20) // lower price
      mm.addOrder(makerSide, maker1)

      val maker2 = Order(userId = 888L, id = 2, price = 0.5, quantity = 100) // higher price
      mm.addOrder(makerSide, maker2)

      val taker = Order(userId = 888L, id = 10, price = 1, quantity = 10)
      val txs = mm.addOrder(takerSide, taker)

      mm().orderMap mustEqual Map(1 -> maker1, 2 -> maker2.copy(quantity = 80))
      mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().limitPriceOrderPool(makerSide) mustEqual SortedSet(maker1, maker2.copy(quantity = 80))
      mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 10, true), Transfer(2, RMB, 20, false)) ::
        Nil
    }

    "match new limit-price taker order fully against multiple limit-price maker orders" in {
      val mm = new MarketManager(BTC ~> RMB)

      val maker1 = Order(userId = 888L, id = 1, price = 1, quantity = 20) // lower price
      mm.addOrder(makerSide, maker1)

      val maker2 = Order(userId = 888L, id = 2, price = 0.5, quantity = 100) // higher price
      mm.addOrder(makerSide, maker2)

      val taker = Order(userId = 888L, id = 10, price = 1, quantity = 60)
      val txs = mm.addOrder(takerSide, taker)

      mm().orderMap mustEqual Map(1 -> maker1.copy(quantity = 10))
      mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().limitPriceOrderPool(makerSide) mustEqual SortedSet(maker1.copy(quantity = 10))
      mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 10, true), Transfer(1, RMB, 10, false)) ::
        Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
        Nil
    }

    "match new limit-price taker order partially against multiple limit-price maker orders" in {
      val mm = new MarketManager(BTC ~> RMB)

      val maker1 = Order(userId = 888L, id = 1, price = 0.5, quantity = 20) // lower price
      mm.addOrder(makerSide, maker1)

      val maker2 = Order(userId = 888L, id = 2, price = 0.4, quantity = 100) // higher price
      mm.addOrder(makerSide, maker2)

      val taker = Order(userId = 888L, id = 10, price = 2, quantity = 90)
      val txs = mm.addOrder(takerSide, taker)

      mm().orderMap mustEqual Map(10 -> taker.copy(quantity = 40)) // 90 - 100x0.4 - 20x0.5
      mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().limitPriceOrderPool(takerSide) mustEqual SortedSet(taker.copy(quantity = 40))

      txs mustEqual
        Transaction(Transfer(10, BTC, 10, false), Transfer(1, RMB, 20, true)) ::
        Transaction(Transfer(10, BTC, 40, false), Transfer(2, RMB, 100, true)) ::
        Nil
    }

    "match new limit-price taker order fully against existing market-price maker order 1" in {
      val mm = new MarketManager(BTC ~> RMB)

      val maker1 = Order(userId = 888L, id = 1, price = 0, quantity = 20) // high priority
      mm.addOrder(makerSide, maker1)

      val maker2 = Order(userId = 888L, id = 2, price = 0, quantity = 100) // low priority
      mm.addOrder(makerSide, maker2)

      val taker = Order(userId = 888L, id = 10, price = 2, quantity = 5)
      val txs = mm.addOrder(takerSide, taker)

      mm().orderMap mustEqual Map(1 -> maker1.copy(quantity = 10), 2 -> maker2)
      mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().marketPriceOrderPool(makerSide) mustEqual SortedSet(maker1.copy(quantity = 10), maker2)

      mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 5, true), Transfer(1, RMB, 10, false)) ::
        Nil
    }

    "match new limit-price taker order fully against existing market-price maker order 2" in {
      val mm = new MarketManager(BTC ~> RMB)

      val maker1 = Order(userId = 888L, id = 1, price = 0, quantity = 20) // higher priority
      mm.addOrder(makerSide, maker1)

      val maker2 = Order(userId = 888L, id = 2, price = 0, quantity = 100) // lower priority
      mm.addOrder(makerSide, maker2)

      val taker = Order(userId = 888L, id = 10, price = 2, quantity = 30)
      val txs = mm.addOrder(takerSide, taker)

      mm().orderMap mustEqual Map(2 -> maker2.copy(quantity = 60))
      mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().marketPriceOrderPool(makerSide) mustEqual SortedSet(maker2.copy(quantity = 60))

      mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().limitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 20, true), Transfer(2, RMB, 40, false)) ::
        Transaction(Transfer(10, BTC, 10, false), Transfer(1, RMB, 20, true)) ::
        Nil
    }

    "match new limit-price taker order partially against existing market-price maker order" in {
      val mm = new MarketManager(BTC ~> RMB)

      val maker1 = Order(userId = 888L, id = 1, price = 0, quantity = 20) // lower price
      mm.addOrder(makerSide, maker1)

      val maker2 = Order(userId = 888L, id = 2, price = 0, quantity = 100) // higher price
      mm.addOrder(makerSide, maker2)

      val taker = Order(userId = 888L, id = 10, price = 2, quantity = 300)
      val txs = mm.addOrder(takerSide, taker)

      mm().orderMap mustEqual Map(10 -> taker.copy(quantity = 240))
      mm().marketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().marketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().limitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().limitPriceOrderPool(takerSide) mustEqual SortedSet(taker.copy(quantity = 240))

      txs mustEqual
        Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
        Transaction(Transfer(10, BTC, 10, false), Transfer(1, RMB, 20, true)) ::
        Nil
    }
  }
}
