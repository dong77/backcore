package com.coinport.coinex.domain

import org.specs2.mutable._
import scala.collection.immutable.SortedSet
import Market._

class MarketMatcherSpec extends Specification {

  val takerSide = MarketSide(BTC, RMB)
  val makerSide = takerSide.reverse

  val market =
    "MarketMatcher" should {
      "allow multiple market-price orders to co-exist" in {
        val mm = new MarketMatcher(BTC, RMB)

        val makerData1 = OrderData(id = 1, price = 0, quantity = 100)
        val maker1 = Order(makerSide, makerData1)
        mm.addOrder(maker1)

        val makerData2 = OrderData(id = 2, price = 0, quantity = 500)
        val maker2 = Order(makerSide, makerData2)
        val txs = mm.addOrder(maker2)

        mm().orderMap mustEqual Map(1L -> maker1, 2 -> maker2)
        mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        mm().getMarketPriceOrderPool(makerSide) mustEqual SortedSet(makerData1, makerData2)
        mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        txs mustEqual Nil
      }

      "NOT match new market-price taker order with existing market-price maker oders" in {
        val mm = new MarketMatcher(BTC, RMB)
        val makerData = OrderData(id = 1, price = 0, quantity = 100)
        val maker = Order(makerSide, makerData)
        mm.addOrder(maker)

        val takerData = OrderData(id = 2, price = 0, quantity = 100)
        val taker = Order(takerSide, takerData)
        val txs = mm.addOrder(taker)

        mm().orderMap mustEqual Map(1L -> maker, 2 -> taker)
        mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
        mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

        mm().getMarketPriceOrderPool(makerSide) mustEqual SortedSet(makerData)
        mm().getMarketPriceOrderPool(takerSide) mustEqual SortedSet(takerData)

        txs mustEqual Nil
      }

      "match new market-price taker order against existing limit-price maker orders and fully execute both orders " +
        "if quantity equals" in {
          val mm = new MarketMatcher(BTC, RMB)
          val makerData = OrderData(id = 1, price = 1, quantity = 100)
          val maker = Order(makerSide, makerData)
          mm.addOrder(maker)

          val takerData = OrderData(id = 2, price = 0, quantity = 100)
          val taker = Order(takerSide, takerData)
          val txs = mm.addOrder(taker)

          mm().orderMap.size mustEqual 0
          mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
          mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          txs mustEqual
            Transaction(Transfer(2, BTC, 100, true), Transfer(1, RMB, 100, true)) ::
            Nil
        }

      "match new market-price taker order against existing limit-price maker orders and fully execute taker orders " +
        "if its quantity is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val makerData = OrderData(id = 1, price = 1, quantity = 100)
          val maker = Order(makerSide, makerData)
          mm.addOrder(maker)

          val takerData = OrderData(id = 2, price = 0, quantity = 10)
          val taker = Order(takerSide, takerData)
          val txs = mm.addOrder(taker)

          mm().orderMap mustEqual Map(1 -> maker.copy(data = makerData.copy(quantity = 90)))
          mm().getLimitPriceOrderPool(makerSide) mustEqual SortedSet(makerData.copy(quantity = 90))
          mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          txs mustEqual
            Transaction(Transfer(2, BTC, 10, true), Transfer(1, RMB, 10, false)) ::
            Nil
        }

      "match new market-price taker order against existing limit-price maker orders and fully execute maker orders " +
        "if its quantity is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val makerData = OrderData(id = 1, price = 1, quantity = 10)
          val maker = Order(makerSide, makerData)
          mm.addOrder(maker)

          val takerData = OrderData(id = 2, price = 0, quantity = 100)
          val taker = Order(takerSide, takerData)
          val txs = mm.addOrder(taker)

          mm().orderMap mustEqual Map(2 -> taker.copy(data = takerData.copy(quantity = 90)))

          mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(takerSide) mustEqual SortedSet(takerData.copy(quantity = 90))

          mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
          mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          txs mustEqual
            Transaction(Transfer(2, BTC, 10, false), Transfer(1, RMB, 10, true)) ::
            Nil
        }

      "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
        "taker order if its quantity is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val makerData1 = OrderData(id = 1, price = 1, quantity = 100) // lower price
          val maker1 = Order(makerSide, makerData1)
          mm.addOrder(maker1)

          val makerData2 = OrderData(id = 2, price = 0.5, quantity = 100) // higher price
          val maker2 = Order(makerSide, makerData2)
          mm.addOrder(maker2)

          val takerData = OrderData(id = 10, price = 0, quantity = 120)
          val taker = Order(takerSide, takerData)
          val txs = mm.addOrder(taker)

          mm().orderMap mustEqual Map(1 -> maker1.copy(data = makerData1.copy(quantity = 30))) //  100 x 0.5 + 100 x 1 - 120 = 30
          mm().getLimitPriceOrderPool(makerSide) mustEqual SortedSet(makerData1.copy(quantity = 30))
          mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          txs mustEqual
            Transaction(Transfer(10, BTC, 70, true), Transfer(1, RMB, 70, false)) ::
            Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
            Nil
        }

      "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
        "all maker orders if their combined quantity is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val makerData1 = OrderData(id = 1, price = 1, quantity = 20) // lower price
          val maker1 = Order(makerSide, makerData1)
          mm.addOrder(maker1)

          val makerData2 = OrderData(id = 2, price = 0.5, quantity = 100) // higher price
          val maker2 = Order(makerSide, makerData2)
          mm.addOrder(maker2)

          val takerData = OrderData(id = 10, price = 0, quantity = 120)
          val taker = Order(takerSide, takerData)
          val txs = mm.addOrder(taker)

          mm().orderMap mustEqual Map(10 -> taker.copy(data = takerData.copy(quantity = 50))) //  120 - 100 x 0.5 + 20 x 1 - 120 = 50
          mm().getMarketPriceOrderPool(takerSide) mustEqual SortedSet(takerData.copy(quantity = 50))
          mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

          mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
          mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

          txs mustEqual
            Transaction(Transfer(10, BTC, 20, false), Transfer(1, RMB, 20, true)) ::
            Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
            Nil
        }

      "match new market-price taker order against as many existing limit-price by order as necessary" in {
        val mm = new MarketMatcher(BTC, RMB)

        val roof = 1000 * 10
        (1 to roof) foreach { i => mm.addOrder(Order(makerSide, OrderData(id = i, price = 1.0 / i, quantity = i))) }

        val txs = mm.addOrder(Order(takerSide, OrderData(id = roof + 1, price = 0, quantity = roof + 1)))

        mm().orderMap mustEqual Map(roof + 1 -> Order(takerSide, OrderData(id = roof + 1, price = 0, quantity = 1)))

        txs.size mustEqual roof
      }
    }

  "MarketMatcher" should {
    "match new limit-price taker order against the highest limit-price maker order" in {
      val mm = new MarketMatcher(BTC, RMB)

      val makerData1 = OrderData(id = 1, price = 1, quantity = 20) // lower price
      val maker1 = Order(makerSide, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2, price = 0.5, quantity = 100) // higher price
      val maker2 = Order(makerSide, makerData2)
      mm.addOrder(maker2)

      val takerData = OrderData(id = 10, price = 1, quantity = 10)
      val taker = Order(takerSide, takerData)
      val txs = mm.addOrder(taker)

      mm().orderMap mustEqual Map(1 -> maker1, 2 -> maker2.copy(data = makerData2.copy(quantity = 80)))
      mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(makerSide) mustEqual SortedSet(makerData1, makerData2.copy(quantity = 80))
      mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 10, true), Transfer(2, RMB, 20, false)) ::
        Nil
    }

    "match new limit-price taker order fully against multiple limit-price maker orders" in {
      val mm = new MarketMatcher(BTC, RMB)

      val makerData1 = OrderData(id = 1, price = 1, quantity = 20) // lower price
      val maker1 = Order(makerSide, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2, price = 0.5, quantity = 100) // higher price
      val maker2 = Order(makerSide, makerData2)
      mm.addOrder(maker2)

      val takerData = OrderData(id = 10, price = 1, quantity = 60)
      val taker = Order(takerSide, takerData)
      val txs = mm.addOrder(taker)

      mm().orderMap mustEqual Map(1 -> maker1.copy(data = makerData1.copy(quantity = 10)))
      mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(makerSide) mustEqual SortedSet(makerData1.copy(quantity = 10))
      mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 10, true), Transfer(1, RMB, 10, false)) ::
        Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
        Nil
    }

    "match new limit-price taker order partially against multiple limit-price maker orders" in {
      val mm = new MarketMatcher(BTC, RMB)

      val makerData1 = OrderData(id = 1, price = 0.5, quantity = 20) // lower price
      val maker1 = Order(makerSide, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2, price = 0.4, quantity = 100) // higher price
      val maker2 = Order(makerSide, makerData2)
      mm.addOrder(maker2)

      val takerData = OrderData(id = 10, price = 2, quantity = 90)
      val taker = Order(takerSide, takerData)
      val txs = mm.addOrder(taker)

      mm().orderMap mustEqual Map(10 -> taker.copy(data = takerData.copy(quantity = 40))) // 90 - 100x0.4 - 20x0.5
      mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(takerSide) mustEqual SortedSet(takerData.copy(quantity = 40))

      txs mustEqual
        Transaction(Transfer(10, BTC, 10, false), Transfer(1, RMB, 20, true)) ::
        Transaction(Transfer(10, BTC, 40, false), Transfer(2, RMB, 100, true)) ::
        Nil
    }

    "match new limit-price taker order fully against existing market-price maker order 1" in {
      val mm = new MarketMatcher(BTC, RMB)

      val makerData1 = OrderData(id = 1, price = 0, quantity = 20) // high priority
      val maker1 = Order(makerSide, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2, price = 0, quantity = 100) // low priority
      val maker2 = Order(makerSide, makerData2)
      mm.addOrder(maker2)

      val takerData = OrderData(id = 10, price = 2, quantity = 5)
      val taker = Order(takerSide, takerData)
      val txs = mm.addOrder(taker)

      mm().orderMap mustEqual Map(1 -> maker1.copy(data = makerData1.copy(quantity = 10)), 2 -> maker2)
      mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(makerSide) mustEqual SortedSet(makerData1.copy(quantity = 10), makerData2)

      mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 5, true), Transfer(1, RMB, 10, false)) ::
        Nil
    }

    "match new limit-price taker order fully against existing market-price maker order 2" in {
      val mm = new MarketMatcher(BTC, RMB)

      val makerData1 = OrderData(id = 1, price = 0, quantity = 20) // higher priority
      val maker1 = Order(makerSide, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2, price = 0, quantity = 100) // lower priority
      val maker2 = Order(makerSide, makerData2)
      mm.addOrder(maker2)

      val takerData = OrderData(id = 10, price = 2, quantity = 30)
      val taker = Order(takerSide, takerData)
      val txs = mm.addOrder(taker)

      mm().orderMap mustEqual Map(2 -> maker2.copy(data = makerData2.copy(quantity = 60)))
      mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(makerSide) mustEqual SortedSet(makerData2.copy(quantity = 60))

      mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(takerSide) mustEqual EmptyOrderPool

      txs mustEqual
        Transaction(Transfer(10, BTC, 20, true), Transfer(2, RMB, 40, false)) ::
        Transaction(Transfer(10, BTC, 10, false), Transfer(1, RMB, 20, true)) ::
        Nil
    }

    "match new limit-price taker order partially against existing market-price maker order" in {
      val mm = new MarketMatcher(BTC, RMB)

      val makerData1 = OrderData(id = 1, price = 0, quantity = 20) // lower price
      val maker1 = Order(makerSide, makerData1)
      mm.addOrder(maker1)

      val makerData2 = OrderData(id = 2, price = 0, quantity = 100) // higher price
      val maker2 = Order(makerSide, makerData2)
      mm.addOrder(maker2)

      val takerData = OrderData(id = 10, price = 2, quantity = 300)
      val taker = Order(takerSide, takerData)
      val txs = mm.addOrder(taker)

      mm().orderMap mustEqual Map(10 -> taker.copy(data = takerData.copy(quantity = 240)))
      mm().getMarketPriceOrderPool(takerSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(makerSide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(makerSide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(takerSide) mustEqual SortedSet(takerData.copy(quantity = 240))

      txs mustEqual
        Transaction(Transfer(10, BTC, 50, false), Transfer(2, RMB, 100, true)) ::
        Transaction(Transfer(10, BTC, 10, false), Transfer(1, RMB, 20, true)) ::
        Nil
    }

    "match new limit-price taker order against as many existing limit-price by order as necessary" in {
      val mm = new MarketMatcher(BTC, RMB)

      val roof = 1000 * 10
      (1 to roof) foreach { i => mm.addOrder(Order(makerSide, OrderData(id = i, price = 1.0 / i, quantity = i))) }

      val txs = mm.addOrder(Order(takerSide, OrderData(id = roof + 1, price = 1, quantity = roof + 1)))

      mm().orderMap mustEqual Map(roof + 1 -> Order(takerSide, OrderData(id = roof + 1, price = 1, quantity = 1)))

      txs.size mustEqual roof
    }
  }
}