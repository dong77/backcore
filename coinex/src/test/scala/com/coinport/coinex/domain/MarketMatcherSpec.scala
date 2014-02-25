package com.coinport.coinex.domain

import org.specs2.mutable._
import scala.collection.immutable.SortedSet
import Market._

class MarketMatcherSpec extends Specification {

  val sellSide = MarketSide(BTC, RMB)
  val buySide = sellSide.reverse

  val market =
    "MarketMatcher" should {
      "allow multiple market-price orders to co-exist" in {
        val mm = new MarketMatcher(BTC, RMB)

        val buyData1 = OrderData(id = 1, price = 0, amount = 100)
        val buy1 = Order(buySide, buyData1)
        mm.addOrder(buy1)

        val buyData2 = OrderData(id = 2, price = 0, amount = 500)
        val buy2 = Order(buySide, buyData2)
        mm.addOrder(buy2)

        mm().orderMap mustEqual Map(1L -> buyData1, 2 -> buyData2)
        mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
        mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool

        mm().getMarketPriceOrderPool(buySide) mustEqual SortedSet(buyData1, buyData2)
        mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
      }

      "NOT match new market-price sell order with existing market-price buy oders" in {
        val mm = new MarketMatcher(BTC, RMB)
        val buyData = OrderData(id = 1, price = 0, amount = 100)
        val buy = Order(buySide, buyData)
        mm.addOrder(buy)

        val sellData = OrderData(id = 2, price = 0, amount = 100)
        val sell = Order(sellSide, sellData)
        mm.addOrder(sell)

        mm().orderMap mustEqual Map(1L -> buyData, 2 -> sellData)
        mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
        mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool

        mm().getMarketPriceOrderPool(buySide) mustEqual SortedSet(buyData)
        mm().getMarketPriceOrderPool(sellSide) mustEqual SortedSet(sellData)

      }

      "match new market-price sell order against existing limit-price buy orders and fully execute both orders " +
        "if amount equals" in {
          val mm = new MarketMatcher(BTC, RMB)
          val buyData = OrderData(id = 1, price = 1, amount = 100)
          val buy = Order(buySide, buyData)
          mm.addOrder(buy)

          val sellData = OrderData(id = 2, price = 0, amount = 100)
          val sell = Order(sellSide, sellData)
          mm.addOrder(sell)

          mm().orderMap.size mustEqual 0
          mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
          mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool

          mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
        }

      "match new market-price sell order against existing limit-price buy orders and fully execute sell orders " +
        "if its amount is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val buyData = OrderData(id = 1, price = 1, amount = 100)
          val buy = Order(buySide, buyData)
          mm.addOrder(buy)

          val sellData = OrderData(id = 2, price = 0, amount = 10)
          val sell = Order(sellSide, sellData)
          mm.addOrder(sell)

          mm().orderMap mustEqual Map(1 -> buyData.copy(amount = 90))
          mm().getLimitPriceOrderPool(buySide) mustEqual SortedSet(buyData.copy(amount = 90))
          mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool

          mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
        }

      "match new market-price sell order against existing limit-price buy orders and fully execute buy orders " +
        "if its amount is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val buyData = OrderData(id = 1, price = 1, amount = 10)
          val buy = Order(buySide, buyData)
          mm.addOrder(buy)

          val sellData = OrderData(id = 2, price = 0, amount = 100)
          val sell = Order(sellSide, sellData)
          mm.addOrder(sell)

          mm().orderMap mustEqual Map(2 -> sellData.copy(amount = 90))

          mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(sellSide) mustEqual SortedSet(sellData.copy(amount = 90))

          mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
          mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool
        }

      "match new market-price sell order against multiple existing limit-price buy orders and fully execute " +
        "sell order if its amount is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val buyData1 = OrderData(id = 1, price = 1, amount = 100) // lower price
          val buy1 = Order(buySide, buyData1)
          mm.addOrder(buy1)

          val buyData2 = OrderData(id = 2, price = 0.5, amount = 100) // higher price
          val buy2 = Order(buySide, buyData2)
          mm.addOrder(buy2)

          val sellData = OrderData(id = 10, price = 0, amount = 120)
          val sell = Order(sellSide, sellData)
          mm.addOrder(sell)

          mm().orderMap mustEqual Map(1 -> buyData1.copy(amount = 30)) //  100 x 0.5 + 100 x 1 - 120 = 30
          mm().getLimitPriceOrderPool(buySide) mustEqual SortedSet(buyData1.copy(amount = 30))
          mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool

          mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool
          mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
        }

      "match new market-price sell order against multiple existing limit-price buy orders and fully execute " +
        "all buy orders if their combined amount is smaller" in {
          val mm = new MarketMatcher(BTC, RMB)
          val buyData1 = OrderData(id = 1, price = 1, amount = 20) // lower price
          val buy1 = Order(buySide, buyData1)
          mm.addOrder(buy1)

          val buyData2 = OrderData(id = 2, price = 0.5, amount = 100) // higher price
          val buy2 = Order(buySide, buyData2)
          mm.addOrder(buy2)

          val sellData = OrderData(id = 10, price = 0, amount = 120)
          val sell = Order(sellSide, sellData)
          mm.addOrder(sell)

          mm().orderMap mustEqual Map(10 -> sellData.copy(amount = 50)) //  120 - 100 x 0.5 + 20 x 1 - 120 = 50
          mm().getMarketPriceOrderPool(sellSide) mustEqual SortedSet(sellData.copy(amount = 50))
          mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool

          mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
          mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool
        }

      "match new market-price sell order against as many existing limit-price by order as necessary" in {
        val mm = new MarketMatcher(BTC, RMB)

        val roof = 1000 * 10
        (1 to roof) foreach { i => mm.addOrder(Order(buySide, OrderData(id = i, price = 1.0 / i, amount = i))) }
        mm.addOrder(Order(sellSide, OrderData(id = roof + 1, price = 0, amount = roof + 1)))

        mm().orderMap mustEqual Map(roof + 1 -> OrderData(id = roof + 1, price = 0, amount = 1))
      }
    }

  "MarketMatcher" should {
    "match new limit-price sell order against the highest limit-price buy order" in {
      val mm = new MarketMatcher(BTC, RMB)

      val buyData1 = OrderData(id = 1, price = 1, amount = 20) // lower price
      val buy1 = Order(buySide, buyData1)
      mm.addOrder(buy1)

      val buyData2 = OrderData(id = 2, price = 0.5, amount = 100) // higher price
      val buy2 = Order(buySide, buyData2)
      mm.addOrder(buy2)

      val sellData = OrderData(id = 10, price = 1, amount = 10)
      val sell = Order(sellSide, sellData)
      mm.addOrder(sell)

      mm().orderMap mustEqual Map(1 -> buyData1, 2 -> buyData2.copy(amount = 80))
      mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(buySide) mustEqual SortedSet(buyData1, buyData2.copy(amount = 80))
      mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool
    }

    "match new limit-price sell order fully against multiple limit-price buy orders" in {
      val mm = new MarketMatcher(BTC, RMB)

      val buyData1 = OrderData(id = 1, price = 1, amount = 20) // lower price
      val buy1 = Order(buySide, buyData1)
      mm.addOrder(buy1)

      val buyData2 = OrderData(id = 2, price = 0.5, amount = 100) // higher price
      val buy2 = Order(buySide, buyData2)
      mm.addOrder(buy2)

      val sellData = OrderData(id = 10, price = 1, amount = 60)
      val sell = Order(sellSide, sellData)
      mm.addOrder(sell)

      mm().orderMap mustEqual Map(1 -> buyData1.copy(amount = 10))
      mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(buySide) mustEqual SortedSet(buyData1.copy(amount = 10))
      mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool
    }

    "match new limit-price sell order partially against multiple limit-price buy orders" in {
      val mm = new MarketMatcher(BTC, RMB)

      val buyData1 = OrderData(id = 1, price = 0.5, amount = 20) // lower price
      val buy1 = Order(buySide, buyData1)
      mm.addOrder(buy1)

      val buyData2 = OrderData(id = 2, price = 0.4, amount = 100) // higher price
      val buy2 = Order(buySide, buyData2)
      mm.addOrder(buy2)

      val sellData = OrderData(id = 10, price = 2, amount = 90)
      val sell = Order(sellSide, sellData)
      mm.addOrder(sell)

      mm().orderMap mustEqual Map(10 -> sellData.copy(amount = 40)) // 90 - 100x0.4 - 20x0.5
      mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(sellSide) mustEqual SortedSet(sellData.copy(amount = 40))
    }

    "match new limit-price sell order fully against existing market-price buy order 1" in {
      val mm = new MarketMatcher(BTC, RMB)

      val buyData1 = OrderData(id = 1, price = 0, amount = 20) // lower price
      val buy1 = Order(buySide, buyData1)
      mm.addOrder(buy1)

      val buyData2 = OrderData(id = 2, price = 0, amount = 100) // higher price
      val buy2 = Order(buySide, buyData2)
      mm.addOrder(buy2)

      val sellData = OrderData(id = 10, price = 2, amount = 5)
      val sell = Order(sellSide, sellData)
      mm.addOrder(sell)

      mm().orderMap mustEqual Map(1 -> buyData1.copy(amount = 10), 2 -> buyData2)
      mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(buySide) mustEqual SortedSet(buyData1.copy(amount = 10), buyData2)

      mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool
    }

    "match new limit-price sell order fully against existing market-price buy order 2" in {
      val mm = new MarketMatcher(BTC, RMB)

      val buyData1 = OrderData(id = 1, price = 0, amount = 20) // higher priority
      val buy1 = Order(buySide, buyData1)
      mm.addOrder(buy1)

      val buyData2 = OrderData(id = 2, price = 0, amount = 100) // lower priority
      val buy2 = Order(buySide, buyData2)
      mm.addOrder(buy2)

      val sellData = OrderData(id = 10, price = 2, amount = 30)
      val sell = Order(sellSide, sellData)
      mm.addOrder(sell)

      mm().orderMap mustEqual Map(2 -> buyData2.copy(amount = 60))
      mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(buySide) mustEqual SortedSet(buyData2.copy(amount = 60))

      mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(sellSide) mustEqual EmptyOrderPool
    }

    "match new limit-price sell order partially against existing market-price buy order" in {
      val mm = new MarketMatcher(BTC, RMB)

      val buyData1 = OrderData(id = 1, price = 0, amount = 20) // lower price
      val buy1 = Order(buySide, buyData1)
      mm.addOrder(buy1)

      val buyData2 = OrderData(id = 2, price = 0, amount = 100) // higher price
      val buy2 = Order(buySide, buyData2)
      mm.addOrder(buy2)

      val sellData = OrderData(id = 10, price = 2, amount = 300)
      val sell = Order(sellSide, sellData)
      mm.addOrder(sell)

      mm().orderMap mustEqual Map(10 -> sellData.copy(amount = 240))
      mm().getMarketPriceOrderPool(sellSide) mustEqual EmptyOrderPool
      mm().getMarketPriceOrderPool(buySide) mustEqual EmptyOrderPool

      mm().getLimitPriceOrderPool(buySide) mustEqual EmptyOrderPool
      mm().getLimitPriceOrderPool(sellSide) mustEqual SortedSet(sellData.copy(amount = 240))
    }

    "match new limit-price sell order against as many existing limit-price by order as necessary" in {
      val mm = new MarketMatcher(BTC, RMB)

      val roof = 1000 * 10
      (1 to roof) foreach { i => mm.addOrder(Order(buySide, OrderData(id = i, price = 1.0 / i, amount = i))) }
      mm.addOrder(Order(sellSide, OrderData(id = roof + 1, price = 1, amount = roof + 1)))

      mm().orderMap mustEqual Map(roof + 1 -> OrderData(id = roof + 1, price = 1, amount = 1))
    }
  }
}