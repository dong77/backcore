/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable._
import scala.collection.immutable.SortedSet

import com.coinport.coinex.data._
import com.coinport.coinex.data.mutable.MarketState._
import Implicits._
import Currency._
import OrderStatus._

class MarketManagerSpec extends Specification {

  val takerSide = Btc ~> Rmb
  val makerSide = takerSide.reverse

  "MarketManager" should {
    "fix the dust bug from robot test" in {
      val manager = new MarketManager(Btc ~> Rmb)
      manager.addOrder(makerSide, Order(-6771488127296557565L, 32, 21930, Some(1.1499999999999999E-4), None, Some(1397457555749L), None, Some(-6771488127296557565L), None, 0))
      manager.addOrder(takerSide, Order(-245561917658914311L, 33, 8, Some(4920.0), None, Some(1397457555805L), None, Some(-245561917658914311L), None, 0)) mustEqual OrderSubmitted(
        OrderInfo(MarketSide(Btc, Rmb), Order(-245561917658914311L, 33, 8, Some(4920.0), None, Some(1397457555805L), None, Some(-245561917658914311L), None, 0), 2, 17391, PartiallyExecuted, Some(1397457555805L)),
        List(Transaction(330000, 1397457555805L, MarketSide(Btc, Rmb), OrderUpdate(Order(-245561917658914311L, 33, 8, Some(4920.0), None, Some(1397457555805L), None, Some(-245561917658914311L), None, 0),
          Order(-245561917658914311L, 33, 6, Some(4920.0), None, Some(1397457555805L), None, Some(-245561917658914311L), None, 17391)),
          OrderUpdate(Order(-6771488127296557565L, 32, 21930, Some(1.1499999999999999E-4), None, Some(1397457555749L), None, Some(-6771488127296557565L), None, 0), Order(-6771488127296557565L, 32, 4539, Some(1.1499999999999999E-4), None, Some(1397457555749L), None, Some(-6771488127296557565L), None, 2)), None)))
      manager().orderMap mustEqual Map(33 -> Order(-245561917658914311L, 33, 6, Some(4920.0), None, Some(1397457555805L), None, Some(-245561917658914311L), None, 17391))
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual SortedSet(Order(-245561917658914311L, 33, 6, Some(4920.0), None, Some(1397457555805L), None, Some(-245561917658914311L), None, 17391))
    }

    "match limit-price order market-price orders can't exists in the market" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val mpo1 = Order(userId = 1, id = 1, price = None, quantity = 30000) // higher priority
      val mpo2 = Order(userId = 2, id = 2, price = None, quantity = 60000)
      val lpo1 = Order(userId = 3, id = 3, price = Some(1.0 / 5000), quantity = 10000) // higher priority
      val lpo2 = Order(userId = 4, id = 4, price = Some(1.0 / 4000), quantity = 40000)
      val taker = Order(userId = 5, id = 5, price = Some(2000), quantity = 100)

      manager.addOrder(makerSide, mpo1)
      manager.addOrder(makerSide, mpo2)
      manager.addOrder(makerSide, lpo1)
      manager.addOrder(makerSide, lpo2)

      val result = manager.addOrder(takerSide, taker)

      val updatedLpo1 = lpo1.copy(quantity = 0, inAmount = 2) // buy 2
      val updatedLpo2 = lpo2.copy(quantity = 0, inAmount = 10) // buy 10
      val updatedMpo1 = mpo1.copy(quantity = 0) // buy 15
      val updatedMpo2 = mpo2.copy(quantity = 0) // buy 30
      val updatedTaker = taker.copy(quantity = 100 - 57)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 12, 50000, PartiallyExecuted, Some(0)),
        Seq(
          Transaction(50000, 0, takerSide, taker --> taker.copy(quantity = 98, inAmount = 10000), lpo1 --> updatedLpo1),
          Transaction(50001, 0, takerSide,
            taker.copy(quantity = 98, inAmount = 10000) --> taker.copy(quantity = 88, inAmount = 50000),
            lpo2 --> updatedLpo2)))
    }
  }

  "MarketManager" should {
    "match limit-price order against existing limit-price order with take-limit and update take-limit" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 666, id = 1, price = Some(1.0 / 4500), quantity = 45099, takeLimit = Some(11))
      val taker = Order(userId = 888, id = 3, price = Some(4000), quantity = 10)

      manager.addOrder(makerSide, maker)
      val result = manager.addOrder(takerSide, taker)

      val updatedMaker = maker.copy(quantity = 99, takeLimit = Some(1), inAmount = 10)
      val updatedTaker = taker.copy(quantity = taker.quantity - 10, inAmount = 45000)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 10, 45000, FullyExecuted, Some(0)),
        Seq(Transaction(30000, 0, takerSide, taker --> updatedTaker, maker --> updatedMaker)))

      manager().orderMap mustEqual Map()
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual EmptyOrderPool
    }

    "match limit-price order with as many existing limit-price order with take-limit" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(1.0 / 4500), quantity = 10000, takeLimit = Some(1))
      val maker2 = Order(userId = 777, id = 2, price = Some(1.0 / 5000), quantity = 15000, takeLimit = Some(3))
      val taker = Order(userId = 888, id = 3, price = Some(4000), quantity = 10, timestamp = Some(0))

      manager.addOrder(makerSide, maker1)
      manager.addOrder(makerSide, maker2)
      val result = manager.addOrder(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = maker1.quantity - 4500, takeLimit = Some(0), inAmount = 1)
      val updatedMaker2 = maker2.copy(quantity = maker2.quantity - 15000, takeLimit = Some(0), inAmount = 3)
      val updatedTaker = taker.copy(quantity = 6, inAmount = 19500)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 4, 19500, PartiallyExecuted, Some(0)),
        Seq(
          Transaction(30000, 0, takerSide,
            taker --> taker.copy(quantity = 7, inAmount = 15000), maker2 --> updatedMaker2),
          Transaction(30001, 0, takerSide,
            taker.copy(quantity = 7, inAmount = 15000) --> updatedTaker, maker1 --> updatedMaker1)))

      manager().orderMap mustEqual Map(3 -> taker.copy(quantity = 6, inAmount = 19500))
      manager().orderPool(takerSide) mustEqual SortedSet(updatedTaker)
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
    }
  }

  "MarketManager" should {
    "market-price orders can't exist in an empty market" in {
      val manager = new MarketManager(Btc ~> Rmb)

      val maker1 = Order(userId = 888L, id = 1, price = None, quantity = 100)
      val maker2 = Order(userId = 888L, id = 2, price = None, quantity = 500)

      val result1 = manager.addOrder(makerSide, maker1)
      val result2 = manager.addOrder(makerSide, maker2)

      val updatedMaker1 = maker1
      val updatedMaker2 = maker2

      result1 mustEqual OrderSubmitted(OrderInfo(makerSide, updatedMaker1, 0, 0, MarketAutoCancelled, None), Nil)
      result2 mustEqual OrderSubmitted(OrderInfo(makerSide, updatedMaker2, 0, 0, MarketAutoCancelled, None), Nil)

      manager().orderMap mustEqual Map()
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual EmptyOrderPool
    }

    "NOT match new market-price taker order" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 888L, id = 1, price = None, quantity = 100)
      val taker = Order(userId = 888L, id = 2, price = None, quantity = 100)

      manager.addOrder(makerSide, maker)
      val result = manager.addOrder(takerSide, taker)

      result.txs mustEqual Nil

      manager().orderMap mustEqual Map()
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual EmptyOrderPool
    }

    "match new market-price taker order against existing limit-price maker orders and fully execute both orders " +
      "if quantity equals" in {
        val manager = new MarketManager(Btc ~> Rmb)

        val maker = Order(userId = 777L, id = 1, price = Some(1), quantity = 100)
        val taker = Order(userId = 888L, id = 2, price = None, quantity = 100)

        manager.addOrder(makerSide, maker)
        val result = manager.addOrder(takerSide, taker)

        val updatedMaker = maker.copy(quantity = 0, inAmount = 100)
        val updatedTaker = taker.copy(quantity = 0, inAmount = 100)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 100, 100, FullyExecuted, Some(0)),
          Seq(
            Transaction(20000, 0, takerSide,
              taker --> updatedTaker,
              maker --> updatedMaker)))

        manager().orderMap.size mustEqual 0
        manager().orderPool(makerSide) mustEqual EmptyOrderPool
        manager().orderPool(takerSide) mustEqual EmptyOrderPool
      }

    "match new market-price taker order against existing limit-price maker orders and fully execute taker orders " +
      "if its quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)

        val maker = Order(userId = 777L, id = 1, price = Some(1), quantity = 100)
        val taker = Order(userId = 888L, id = 2, price = None, quantity = 10)

        manager.addOrder(makerSide, maker)
        val result = manager.addOrder(takerSide, taker)

        val updatedMaker = maker.copy(quantity = 90, inAmount = 10)
        val updatedTaker = taker.copy(quantity = 0, inAmount = 10)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 10, 10, FullyExecuted, Some(0)),
          Seq(Transaction(20000, 0, takerSide,
            taker --> updatedTaker,
            maker --> updatedMaker)))

        manager().orderMap mustEqual Map(1 -> updatedMaker)
        manager().orderPool(makerSide) mustEqual SortedSet(updatedMaker)
        manager().orderPool(takerSide) mustEqual EmptyOrderPool

      }

    "match new market-price taker order against existing limit-price maker orders and fully execute maker orders " +
      "if its quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)
        val maker = Order(userId = 777L, id = 1, price = Some(1), quantity = 10)
        val taker = Order(userId = 888L, id = 2, price = None, quantity = 100)

        manager.addOrder(makerSide, maker)
        val result = manager.addOrder(takerSide, taker)

        val updatedMaker = maker.copy(quantity = 0, inAmount = 10)
        val updatedTaker = taker.copy(quantity = 90, inAmount = 10)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 10, 10, MarketAutoPartiallyCancelled, Some(0)),
          Seq(Transaction(20000, 0, takerSide, taker --> updatedTaker, maker --> updatedMaker)))

        manager().orderMap mustEqual Map()
        manager().orderPool(makerSide) mustEqual EmptyOrderPool
        manager().orderPool(takerSide) mustEqual EmptyOrderPool
      }

    "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
      "taker order if its quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)

        val maker1 = Order(userId = 666L, id = 1, price = Some(1), quantity = 100) // lower price
        val maker2 = Order(userId = 777L, id = 2, price = Some(0.5), quantity = 100) // higher price
        val taker = Order(userId = 888L, id = 10, price = None, quantity = 120)

        manager.addOrder(makerSide, maker1)
        manager.addOrder(makerSide, maker2)
        val result = manager.addOrder(takerSide, taker)

        val updatedMaker1 = maker1.copy(quantity = 30, inAmount = 70)
        val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 50)
        val updatedTaker = taker.copy(quantity = 0, inAmount = 170)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 120, 170, FullyExecuted, Some(0)),
          Seq(
            Transaction(100000, 0, takerSide,
              taker --> taker.copy(quantity = 70, inAmount = 100), maker2 --> updatedMaker2),
            Transaction(100001, 0, takerSide,
              taker.copy(quantity = 70, inAmount = 100) --> updatedTaker, maker1 --> updatedMaker1)))

        manager().orderMap mustEqual Map(1 -> updatedMaker1) //  100 x 0.5 + 100 x 1 - 120 = 30
        manager().orderPool(makerSide) mustEqual SortedSet(updatedMaker1)
        manager().orderPool(takerSide) mustEqual EmptyOrderPool
      }

    "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
      "all maker orders if their combined quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)
        val maker1 = Order(userId = 666L, id = 1, price = Some(1), quantity = 20) // lower price
        val maker2 = Order(userId = 777L, id = 2, price = Some(0.5), quantity = 100) // higher price
        val taker = Order(userId = 888L, id = 10, price = None, quantity = 120)

        manager.addOrder(makerSide, maker1)
        manager.addOrder(makerSide, maker2)
        val result = manager.addOrder(takerSide, taker)

        val updatedMaker1 = maker1.copy(quantity = 0, inAmount = 20)
        val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 50)
        val updatedTaker = taker.copy(quantity = 50, inAmount = 120)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 70, 120, MarketAutoPartiallyCancelled, Some(0)),
          Seq(
            Transaction(100000, 0, takerSide,
              taker --> taker.copy(quantity = 70, inAmount = 100), maker2 --> updatedMaker2),
            Transaction(100001, 0, takerSide,
              taker.copy(quantity = 70, inAmount = 100) --> updatedTaker, maker1 --> updatedMaker1)))

        manager().orderMap mustEqual Map()
        manager().orderPool(makerSide) mustEqual EmptyOrderPool
        manager().orderPool(takerSide) mustEqual EmptyOrderPool
      }
  }

  "MarketManager" should {
    "match new limit-price taker order against the highest limit-price maker order" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(1), quantity = 20) // lower price
      val maker2 = Order(userId = 777, id = 2, price = Some(0.5), quantity = 100) // higher price
      val taker = Order(userId = 888L, id = 10, price = Some(1), quantity = 10)

      manager.addOrder(makerSide, maker1)
      manager.addOrder(makerSide, maker2)

      val result = manager.addOrder(takerSide, taker)
      val updatedMaker2 = maker2.copy(quantity = 80, inAmount = 10)
      val updatedTaker = taker.copy(quantity = 0, inAmount = 20)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 10, 20, FullyExecuted, Some(0)),
        Seq(Transaction(100000, 0, takerSide, taker --> updatedTaker, maker2 --> updatedMaker2)))

      manager().orderMap mustEqual Map(1 -> maker1, 2 -> updatedMaker2)
      manager().orderPool(makerSide) mustEqual SortedSet(maker1, updatedMaker2)
      manager().orderPool(takerSide) mustEqual EmptyOrderPool
    }

    "match new limit-price taker order fully against multiple limit-price maker orders" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(1), quantity = 20, timestamp = Some(11111)) // lower price
      val maker2 = Order(userId = 777, id = 2, price = Some(0.5), quantity = 100, timestamp = Some(22222)) // higher price
      val taker = Order(userId = 888L, id = 10, price = Some(1), quantity = 60, timestamp = Some(33333))

      manager.addOrder(makerSide, maker1)
      manager.addOrder(makerSide, maker2)
      val result = manager.addOrder(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = 10, inAmount = 10)
      val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 50)
      val updatedTaker = taker.copy(quantity = 0, inAmount = 110)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 60, 110, FullyExecuted, Some(33333)),
        Seq(
          Transaction(100000, 33333, takerSide,
            taker --> taker.copy(quantity = 10, inAmount = 100), maker2 --> updatedMaker2),
          Transaction(100001, 33333, takerSide,
            taker.copy(quantity = 10, inAmount = 100) --> updatedTaker, maker1 --> updatedMaker1)))

      manager().orderMap mustEqual Map(1 -> maker1.copy(quantity = 10, inAmount = 10))
      manager().orderPool(makerSide) mustEqual SortedSet(maker1.copy(quantity = 10))
      manager().orderPool(takerSide) mustEqual EmptyOrderPool
    }

    "match new limit-price taker order partially against multiple limit-price maker orders" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(0.5), quantity = 20) // lower price
      val maker2 = Order(userId = 777, id = 2, price = Some(0.4), quantity = 100) // higher price
      val taker = Order(userId = 888L, id = 10, price = Some(2), quantity = 90)

      manager.addOrder(makerSide, maker1)
      manager.addOrder(makerSide, maker2)
      val result = manager.addOrder(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = 0, inAmount = 10)
      val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 40)
      val updatedTaker = taker.copy(quantity = 40, inAmount = 120)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 50, 120, PartiallyExecuted, Some(0)),
        Seq(
          Transaction(100000, 0, takerSide,
            taker --> taker.copy(quantity = 50, inAmount = 100), maker2 --> updatedMaker2),
          Transaction(100001, 0, takerSide,
            taker.copy(quantity = 50, inAmount = 100) --> updatedTaker, maker1 --> updatedMaker1)))

      manager().orderMap mustEqual Map(10 -> updatedTaker) // 90 - 100x0.4 - 20x0.5
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual SortedSet(updatedTaker)
    }

    "match new limit-price taker order fully against existing market-price maker order 1" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = None, quantity = 20) // high priority
      val maker2 = Order(userId = 777, id = 2, price = None, quantity = 100) // low priority
      val taker = Order(userId = 888L, id = 10, price = Some(2), quantity = 5)

      manager.addOrder(makerSide, maker1)
      manager.addOrder(makerSide, maker2)
      val result = manager.addOrder(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = 10)
      val updatedTaker = taker.copy(quantity = 0)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 0, 0, Pending, None),
        Seq())

      manager().orderMap mustEqual Map(10 -> taker)
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual SortedSet(taker)
    }
  }

  "MarketManager" should {
    "be able to handle dust" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 1, id = 1, price = Some(500.1), quantity = 1)
      val taker = Order(userId = 5, id = 5, price = None, quantity = 900)

      manager.addOrder(makerSide, maker)
      val result = manager.addOrder(takerSide, taker)

      val updatedMaker = maker.copy(quantity = 0, inAmount = 500)
      val updatedTaker = taker.copy(quantity = 400, inAmount = 1)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 500, 1, MarketAutoPartiallyCancelled, Some(0)),
        Seq(Transaction(50000, 0, takerSide, taker --> updatedTaker, maker --> updatedMaker)))
    }

    "be able to handle dust when price is really small" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 1, id = 1, price = Some(0.15), quantity = 1000)
      val taker = Order(userId = 5, id = 5, price = None, quantity = 180)

      manager.addOrder(makerSide, maker)
      val result = manager.addOrder(takerSide, taker)

      val updatedMaker = maker.copy(quantity = 0, inAmount = 150)
      val updatedTaker = taker.copy(quantity = 30, inAmount = 1000)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 150, 1000, MarketAutoPartiallyCancelled, Some(0)),
        Seq(Transaction(50000, 0, takerSide, taker --> updatedTaker, maker --> updatedMaker)))
    }
  }

  "MarketManager" should {
    "drop order which has onlyTaker flag" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val taker = Order(userId = 888L, id = 2, price = Some(3000), quantity = 100, onlyTaker = Some(true))

      val result = manager.addOrder(takerSide, taker)

      result.txs mustEqual Nil
      manager().orderMap mustEqual Map()
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual EmptyOrderPool
    }

    "drop order which has onlyTaker flag after match" in {
      val side = (Btc ~> Rmb)
      val manager = new MarketManager(side)
      val maker = Order(userId = 888L, id = 1, price = Some(1.0 / 5000), quantity = 100 * 5000)
      val taker = Order(userId = 888L, id = 2, price = Some(3000), quantity = 1000, onlyTaker = Some(true))

      manager.addOrder(makerSide, maker)
      val result = manager.addOrder(takerSide, taker)

      result mustEqual OrderSubmitted(
        OrderInfo(side, taker, 100, 500000, MarketAutoPartiallyCancelled, Some(0)),
        Seq(Transaction(20000, 0, side,
          taker --> taker.copy(quantity = 900, inAmount = 5000 * 100),
          maker --> maker.copy(quantity = 0, inAmount = 100)))
      )

      manager().orderMap mustEqual Map()
      manager().orderPool(makerSide) mustEqual EmptyOrderPool
      manager().orderPool(takerSide) mustEqual EmptyOrderPool
    }
  }
}
