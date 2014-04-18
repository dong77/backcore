/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable._
import scala.collection.immutable.SortedSet

import com.coinport.coinex.data._
import Implicits._
import Currency._
import OrderStatus._
import RefundReason._

class MarketManagerSpec extends Specification {
  val takerSide = Btc ~> Rmb
  val makerSide = takerSide.reverse

  import MarketManager._

  "MarketManager" should {
    "take limit can't block the current transaction" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 1, id = 1, price = Some(1.0 / 2000), quantity = 20000)
      val maker2 = Order(userId = 2, id = 2, price = Some(1.0 / 5000), quantity = 50000)
      val taker = Order(userId = 3, id = 3, price = Some(2000), quantity = 1, takeLimit = Some(2000))

      manager.addOrderToMarket(makerSide, maker1)
      manager.addOrderToMarket(makerSide, maker2)

      manager.addOrderToMarket(takerSide, taker) mustEqual OrderSubmitted(
        OrderInfo(MarketSide(Btc, Rmb),
          Order(3, 3, 1, Some(2000.0), Some(2000), None, None, None, None, 0, None), 1, 5000, FullyExecuted, Some(0)),
        List(Transaction(3000001, 0, MarketSide(Btc, Rmb),
          OrderUpdate(
            Order(3, 3, 1, Some(2000.0), Some(2000), None, None, None, None, 0, None),
            Order(3, 3, 0, Some(2000.0), Some(-3000), None, None, None, None, 5000, None)),
          OrderUpdate(
            Order(2, 2, 50000, Some(2.0E-4), None, None, None, None, None, 0, None),
            Order(2, 2, 45000, Some(2.0E-4), None, None, None, None, None, 1, None)), None)))
    }

    "refund first dust taker" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val taker = Order(userId = 1, id = 1, price = Some(1.0 / 2000), quantity = 200)

      manager.addOrderToMarket(makerSide, taker) mustEqual OrderSubmitted(OrderInfo(MarketSide(Rmb, Btc),
        Order(1, 1, 200, Some(5.0E-4), None, None, None, None, None, 0, Some(Dust)), 0, 0, FullyExecuted, None), List())
    }

    "change the last taker order in tx" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 0, id = 0, price = Some(2000), quantity = 1)
      val taker = Order(userId = 1, id = 1, price = Some(1.0 / 2000), quantity = 2200)

      manager.addOrderToMarket(makerSide, maker)

      manager.addOrderToMarket(takerSide, taker) mustEqual OrderSubmitted(
        OrderInfo(MarketSide(Btc, Rmb),
          Order(1, 1, 2200, Some(5.0E-4), None, None, None, None, None, 0, None), 2000, 1, FullyExecuted, Some(0)),
        List(Transaction(1000001, 0, MarketSide(Btc, Rmb),
          OrderUpdate(
            Order(1, 1, 2200, Some(5.0E-4), None, None, None, None, None, 0, None),
            Order(1, 1, 200, Some(5.0E-4), None, None, None, None, None, 1, Some(Dust))),
          OrderUpdate(
            Order(0, 0, 1, Some(2000.0), None, None, None, None, None, 0, None),
            Order(0, 0, 0, Some(2000.0), None, None, None, None, None, 2000, None)), None)))
    }

    "fix the dust bug from robot test" in {
      val manager = new MarketManager(Btc ~> Rmb)

      manager.addOrderToMarket(makerSide, Order(12345, 32, 21930, Some(1.1499999999999999E-4), None, Some(1397457555749L), None, Some(12345), None, 0))
      manager.addOrderToMarket(takerSide, Order(456789, 33, 8, Some(4920.0), None, Some(1397457555805L), None, Some(456789), None, 0)) mustEqual
        OrderSubmitted(
          OrderInfo(MarketSide(Btc, Rmb), Order(456789, 33, 8, Some(4920.0), None, Some(1397457555805L), None, Some(456789), None, 0), 2, 17391, PartiallyExecuted, Some(1397457555805L)),
          List(
            Transaction(33000001, 1397457555805L, MarketSide(Btc, Rmb),
              OrderUpdate(
                Order(456789, 33, 8, Some(4920.0), None, Some(1397457555805L), None, Some(456789), None, 0),
                Order(456789, 33, 6, Some(4920.0), None, Some(1397457555805L), None, Some(456789), None, 17391)),
              OrderUpdate(
                Order(12345, 32, 21930, Some(1.1499999999999999E-4), None, Some(1397457555749L), None, Some(12345), None, 0),
                Order(12345, 32, 4539, Some(1.1499999999999999E-4), None, Some(1397457555749L), None, Some(12345), None, 2, Some(Dust))), None)))

      manager.orderMap mustEqual Map(33 -> Order(456789, 33, 6, Some(4920.0), None, Some(1397457555805L), None, Some(456789), None, 17391))
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet(Order(456789, 33, 6, Some(4920.0), None, Some(1397457555805L), None, Some(456789), None, 17391))
    }

    "match limit-price order market-price orders can't exists in the market" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val mpo1 = Order(userId = 1, id = 1, price = None, quantity = 30000) // higher priority
      val mpo2 = Order(userId = 2, id = 2, price = None, quantity = 60000)
      val lpo1 = Order(userId = 3, id = 3, price = Some(1.0 / 5000), quantity = 10000) // higher priority
      val lpo2 = Order(userId = 4, id = 4, price = Some(1.0 / 4000), quantity = 40000)
      val taker = Order(userId = 5, id = 5, price = Some(2000), quantity = 100)

      manager.addOrderToMarket(makerSide, mpo1)
      manager.addOrderToMarket(makerSide, mpo2)
      manager.addOrderToMarket(makerSide, lpo1)
      manager.addOrderToMarket(makerSide, lpo2)

      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedLpo1 = lpo1.copy(quantity = 0, inAmount = 2) // buy 2
      val updatedLpo2 = lpo2.copy(quantity = 0, inAmount = 10) // buy 10
      val updatedMpo1 = mpo1.copy(quantity = 0) // buy 15
      val updatedMpo2 = mpo2.copy(quantity = 0) // buy 30
      val updatedTaker = taker.copy(quantity = 100 - 57)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 12, 50000, PartiallyExecuted, Some(0)),
        Seq(
          Transaction(5000001, 0, takerSide, taker --> taker.copy(quantity = 98, inAmount = 10000), lpo1 --> updatedLpo1),
          Transaction(5000002, 0, takerSide,
            taker.copy(quantity = 98, inAmount = 10000) --> taker.copy(quantity = 88, inAmount = 50000),
            lpo2 --> updatedLpo2)))
    }
  }

  "MarketManager" should {
    "match limit-price order against existing limit-price order with take-limit and update take-limit" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 666, id = 1, price = Some(1.0 / 4500), quantity = 45099, takeLimit = Some(11))
      val taker = Order(userId = 888, id = 2, price = Some(4000), quantity = 10)

      manager.addOrderToMarket(makerSide, maker)
      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedMaker = maker.copy(quantity = 99, takeLimit = Some(1), inAmount = 10)
      val updatedTaker = taker.copy(quantity = taker.quantity - 10, inAmount = 45000)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 10, 45000, FullyExecuted, Some(0)),
        Seq(Transaction(2000001, 0, takerSide, taker --> updatedTaker, maker --> updatedMaker.copy(refundReason = Some(Dust)))))

      manager.orderMap mustEqual Map()
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
    }

    "match limit-price order with as many existing limit-price order with take-limit" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(1.0 / 4500), quantity = 10000, takeLimit = Some(1))
      val maker2 = Order(userId = 777, id = 2, price = Some(1.0 / 5000), quantity = 15000, takeLimit = Some(3))
      val taker = Order(userId = 888, id = 3, price = Some(4000), quantity = 10, timestamp = Some(0))

      manager.addOrderToMarket(makerSide, maker1)
      manager.addOrderToMarket(makerSide, maker2)
      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = maker1.quantity - 4500, takeLimit = Some(0), inAmount = 1)
      val updatedMaker2 = maker2.copy(quantity = maker2.quantity - 15000, takeLimit = Some(0), inAmount = 3)
      val updatedTaker = taker.copy(quantity = 6, inAmount = 19500)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 4, 19500, PartiallyExecuted, Some(0)),
        Seq(
          Transaction(3000001, 0, takerSide, taker --> taker.copy(quantity = 7, inAmount = 15000),
            maker2 --> updatedMaker2),
          Transaction(3000002, 0, takerSide, taker.copy(quantity = 7, inAmount = 15000) --> updatedTaker,
            maker1 --> updatedMaker1.copy(refundReason = Some(HitTakeLimit)))))

      manager.orderMap mustEqual Map(3 -> taker.copy(quantity = 6, inAmount = 19500))
      manager.orderPool(takerSide) mustEqual SortedSet(updatedTaker)
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
    }
  }

  "MarketManager" should {
    "market-price orders can't exist in an empty market" in {
      val manager = new MarketManager(Btc ~> Rmb)

      val maker1 = Order(userId = 888L, id = 1, price = None, quantity = 100)
      val maker2 = Order(userId = 888L, id = 2, price = None, quantity = 500)

      val result1 = manager.addOrderToMarket(makerSide, maker1)
      val result2 = manager.addOrderToMarket(makerSide, maker2)

      val updatedMaker1 = maker1
      val updatedMaker2 = maker2

      result1 mustEqual OrderSubmitted(OrderInfo(makerSide, updatedMaker1.copy(refundReason = Some(AutoCancelled)), 0, 0,
        CancelledByMarket, None), Nil)
      result2 mustEqual OrderSubmitted(OrderInfo(makerSide, updatedMaker2.copy(refundReason = Some(AutoCancelled)), 0, 0,
        CancelledByMarket, None), Nil)

      manager.orderMap mustEqual Map()
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
    }

    "NOT match new market-price taker order" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 888L, id = 1, price = None, quantity = 100)
      val taker = Order(userId = 888L, id = 2, price = None, quantity = 100)

      manager.addOrderToMarket(makerSide, maker)
      val result = manager.addOrderToMarket(takerSide, taker)

      result.txs mustEqual Nil

      manager.orderMap mustEqual Map()
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
    }

    "match new market-price taker order against existing limit-price maker orders and fully execute both orders " +
      "if quantity equals" in {
        val manager = new MarketManager(Btc ~> Rmb)

        val maker = Order(userId = 777L, id = 1, price = Some(1), quantity = 100)
        val taker = Order(userId = 888L, id = 2, price = None, quantity = 100)

        manager.addOrderToMarket(makerSide, maker)
        val result = manager.addOrderToMarket(takerSide, taker)

        val updatedMaker = maker.copy(quantity = 0, inAmount = 100)
        val updatedTaker = taker.copy(quantity = 0, inAmount = 100)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 100, 100, FullyExecuted, Some(0)),
          Seq(
            Transaction(2000001, 0, takerSide,
              taker --> updatedTaker,
              maker --> updatedMaker)))

        manager.orderMap.size mustEqual 0
        manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
        manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
      }

    "match new market-price taker order against existing limit-price maker orders and fully execute taker orders " +
      "if its quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)

        val maker = Order(userId = 777L, id = 1, price = Some(1), quantity = 100)
        val taker = Order(userId = 888L, id = 2, price = None, quantity = 10)

        manager.addOrderToMarket(makerSide, maker)
        val result = manager.addOrderToMarket(takerSide, taker)

        val updatedMaker = maker.copy(quantity = 90, inAmount = 10)
        val updatedTaker = taker.copy(quantity = 0, inAmount = 10)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 10, 10, FullyExecuted, Some(0)),
          Seq(Transaction(2000001, 0, takerSide,
            taker --> updatedTaker,
            maker --> updatedMaker)))

        manager.orderMap mustEqual Map(1 -> updatedMaker)
        manager.orderPool(makerSide) mustEqual SortedSet(updatedMaker)
        manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]

      }

    "match new market-price taker order against existing limit-price maker orders and fully execute maker orders " +
      "if its quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)
        val maker = Order(userId = 777L, id = 1, price = Some(1), quantity = 10)
        val taker = Order(userId = 888L, id = 2, price = None, quantity = 100)

        manager.addOrderToMarket(makerSide, maker)
        val result = manager.addOrderToMarket(takerSide, taker)

        val updatedMaker = maker.copy(quantity = 0, inAmount = 10)
        val updatedTaker = taker.copy(quantity = 90, inAmount = 10)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 10, 10, PartiallyExecutedThenCancelledByMarket, Some(0)),
          Seq(Transaction(2000001, 0, takerSide, taker --> updatedTaker.copy(refundReason = Some(AutoCancelled)),
            maker --> updatedMaker)))

        manager.orderMap mustEqual Map()
        manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
        manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
      }

    "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
      "taker order if its quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)

        val maker1 = Order(userId = 666L, id = 1, price = Some(1), quantity = 100) // lower price
        val maker2 = Order(userId = 777L, id = 2, price = Some(0.5), quantity = 100) // higher price
        val taker = Order(userId = 888L, id = 3, price = None, quantity = 120)

        manager.addOrderToMarket(makerSide, maker1)
        manager.addOrderToMarket(makerSide, maker2)
        val result = manager.addOrderToMarket(takerSide, taker)

        val updatedMaker1 = maker1.copy(quantity = 30, inAmount = 70)
        val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 50)
        val updatedTaker = taker.copy(id = 3, quantity = 0, inAmount = 170)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 120, 170, FullyExecuted, Some(0)),
          Seq(
            Transaction(3000001, 0, takerSide,
              taker --> taker.copy(quantity = 70, inAmount = 100), maker2 --> updatedMaker2),
            Transaction(3000002, 0, takerSide,
              taker.copy(quantity = 70, inAmount = 100) --> updatedTaker, maker1 --> updatedMaker1)))

        manager.orderMap mustEqual Map(1 -> updatedMaker1) //  100 x 0.5 + 100 x 1 - 120 = 30
        manager.orderPool(makerSide) mustEqual SortedSet(updatedMaker1)
        manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
      }

    "match new market-price taker order against multiple existing limit-price maker orders and fully execute " +
      "all maker orders if their combined quantity is smaller" in {
        val manager = new MarketManager(Btc ~> Rmb)
        val maker1 = Order(userId = 666L, id = 1, price = Some(1), quantity = 20) // lower price
        val maker2 = Order(userId = 777L, id = 2, price = Some(0.5), quantity = 100) // higher price
        val taker = Order(userId = 888L, id = 3, price = None, quantity = 120)

        manager.addOrderToMarket(makerSide, maker1)
        manager.addOrderToMarket(makerSide, maker2)
        val result = manager.addOrderToMarket(takerSide, taker)

        val updatedMaker1 = maker1.copy(quantity = 0, inAmount = 20)
        val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 50)
        val updatedTaker = taker.copy(quantity = 50, inAmount = 120)

        result mustEqual OrderSubmitted(
          OrderInfo(takerSide, taker, 70, 120, PartiallyExecutedThenCancelledByMarket, Some(0)),
          Seq(
            Transaction(3000001, 0, takerSide,
              taker --> taker.copy(quantity = 70, inAmount = 100), maker2 --> updatedMaker2),
            Transaction(3000002, 0, takerSide,
              taker.copy(quantity = 70, inAmount = 100) --> updatedTaker.copy(refundReason = Some(AutoCancelled)),
              maker1 --> updatedMaker1)))

        manager.orderMap mustEqual Map()
        manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
        manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
      }
  }

  "MarketManager" should {
    "match new limit-price taker order against the highest limit-price maker order" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(1), quantity = 20) // lower price
      val maker2 = Order(userId = 777, id = 2, price = Some(0.5), quantity = 100) // higher price
      val taker = Order(userId = 888L, id = 3, price = Some(1), quantity = 10)

      manager.addOrderToMarket(makerSide, maker1)
      manager.addOrderToMarket(makerSide, maker2)

      val result = manager.addOrderToMarket(takerSide, taker)
      val updatedMaker2 = maker2.copy(quantity = 80, inAmount = 10)
      val updatedTaker = taker.copy(quantity = 0, inAmount = 20)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 10, 20, FullyExecuted, Some(0)),
        Seq(Transaction(3000001, 0, takerSide, taker --> updatedTaker, maker2 --> updatedMaker2)))

      manager.orderMap mustEqual Map(1 -> maker1, 2 -> updatedMaker2)
      manager.orderPool(makerSide) mustEqual SortedSet(maker1, updatedMaker2)
      manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
    }

    "match new limit-price taker order fully against multiple limit-price maker orders" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(1), quantity = 20, timestamp = Some(11111)) // lower price
      val maker2 = Order(userId = 777, id = 2, price = Some(0.5), quantity = 100, timestamp = Some(22222)) // higher price
      val taker = Order(userId = 888L, id = 3, price = Some(1), quantity = 60, timestamp = Some(33333))

      manager.addOrderToMarket(makerSide, maker1)
      manager.addOrderToMarket(makerSide, maker2)
      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = 10, inAmount = 10)
      val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 50)
      val updatedTaker = taker.copy(quantity = 0, inAmount = 110)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 60, 110, FullyExecuted, Some(33333)),
        Seq(
          Transaction(3000001, 33333, takerSide,
            taker --> taker.copy(quantity = 10, inAmount = 100), maker2 --> updatedMaker2),
          Transaction(3000002, 33333, takerSide,
            taker.copy(quantity = 10, inAmount = 100) --> updatedTaker, maker1 --> updatedMaker1)))

      manager.orderMap mustEqual Map(1 -> maker1.copy(quantity = 10, inAmount = 10))
      manager.orderPool(makerSide) mustEqual SortedSet(maker1.copy(quantity = 10))
      manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
    }

    "match new limit-price taker order partially against multiple limit-price maker orders" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = Some(0.5), quantity = 20) // lower price
      val maker2 = Order(userId = 777, id = 2, price = Some(0.4), quantity = 100) // higher price
      val taker = Order(userId = 888L, id = 3, price = Some(2), quantity = 90)

      manager.addOrderToMarket(makerSide, maker1)
      manager.addOrderToMarket(makerSide, maker2)
      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = 0, inAmount = 10)
      val updatedMaker2 = maker2.copy(quantity = 0, inAmount = 40)
      val updatedTaker = taker.copy(quantity = 40, inAmount = 120)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 50, 120, PartiallyExecuted, Some(0)),
        Seq(
          Transaction(3000001, 0, takerSide,
            taker --> taker.copy(quantity = 50, inAmount = 100), maker2 --> updatedMaker2),
          Transaction(3000002, 0, takerSide,
            taker.copy(quantity = 50, inAmount = 100) --> updatedTaker, maker1 --> updatedMaker1)))

      manager.orderMap mustEqual Map(3 -> updatedTaker) // 90 - 100x0.4 - 20x0.5
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet(updatedTaker)
    }

    "match new limit-price taker order fully against existing market-price maker order 1" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker1 = Order(userId = 666, id = 1, price = None, quantity = 20) // high priority
      val maker2 = Order(userId = 777, id = 2, price = None, quantity = 100) // low priority
      val taker = Order(userId = 888L, id = 3, price = Some(2), quantity = 5)

      manager.addOrderToMarket(makerSide, maker1)
      manager.addOrderToMarket(makerSide, maker2)
      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedMaker1 = maker1.copy(quantity = 10)
      val updatedTaker = taker.copy(quantity = 0)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 0, 0, Pending, None),
        Seq())

      manager.orderMap mustEqual Map(3 -> taker)
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet(taker)
    }
  }

  "MarketManager" should {
    "be able to handle dust" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 1, id = 1, price = Some(500.1), quantity = 1)
      val taker = Order(userId = 5, id = 2, price = None, quantity = 900)

      manager.addOrderToMarket(makerSide, maker)
      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedMaker = maker.copy(quantity = 0, inAmount = 500)
      val updatedTaker = taker.copy(quantity = 400, inAmount = 1)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 500, 1, PartiallyExecutedThenCancelledByMarket, Some(0)),
        Seq(Transaction(2000001, 0, takerSide, taker --> updatedTaker.copy(refundReason = Some(AutoCancelled)),
          maker --> updatedMaker)))
    }

    "be able to handle dust when price is really small" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val maker = Order(userId = 1, id = 1, price = Some(0.15), quantity = 1000)
      val taker = Order(userId = 5, id = 2, price = None, quantity = 180)

      manager.addOrderToMarket(makerSide, maker)
      val result = manager.addOrderToMarket(takerSide, taker)

      val updatedMaker = maker.copy(quantity = 0, inAmount = 150)
      val updatedTaker = taker.copy(quantity = 30, inAmount = 1000)

      result mustEqual OrderSubmitted(
        OrderInfo(takerSide, taker, 150, 1000, PartiallyExecutedThenCancelledByMarket, Some(0)),
        Seq(Transaction(2000001, 0, takerSide, taker --> updatedTaker.copy(refundReason = Some(AutoCancelled)),
          maker --> updatedMaker)))
    }
  }

  "MarketManager" should {
    "drop order which has onlyTaker flag" in {
      val manager = new MarketManager(Btc ~> Rmb)
      val taker = Order(userId = 888L, id = 1, price = Some(3000), quantity = 100, onlyTaker = Some(true))

      val result = manager.addOrderToMarket(takerSide, taker)

      result.txs mustEqual Nil
      manager.orderMap mustEqual Map()
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
    }

    "drop order which has onlyTaker flag after match" in {
      val side = (Btc ~> Rmb)
      val manager = new MarketManager(side)
      val maker = Order(userId = 888L, id = 1, price = Some(1.0 / 5000), quantity = 100 * 5000)
      val taker = Order(userId = 888L, id = 2, price = Some(3000), quantity = 1000, onlyTaker = Some(true))

      manager.addOrderToMarket(makerSide, maker)
      val result = manager.addOrderToMarket(takerSide, taker)

      result mustEqual OrderSubmitted(
        OrderInfo(side, taker, 100, 500000, PartiallyExecutedThenCancelledByMarket, Some(0)),
        Seq(Transaction(2000001, 0, side,
          taker --> taker.copy(quantity = 900, inAmount = 5000 * 100, refundReason = Some(AutoCancelled)),
          maker --> maker.copy(quantity = 0, inAmount = 100)))
      )

      manager.orderMap mustEqual Map()
      manager.orderPool(makerSide) mustEqual SortedSet.empty[Order]
      manager.orderPool(takerSide) mustEqual SortedSet.empty[Order]
    }
  }
}
