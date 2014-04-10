/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable._
import com.coinport.coinex.data._
import scala.collection.immutable.SortedMap
import Implicits._
import Currency._

class MarketDepthManagerSpec extends Specification {

  val side = Btc ~> Rmb
  "MarketDepthManager" should {

    "NOT calculate change askMap for market price sell orders" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 10, price = None)
      manager.adjustAmount(side, order, true)
      manager.askMap.isEmpty mustEqual true
    }

    "calculate the right amount for new sell orders without takeLimit" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 10, price = Some(100.0))
      manager.adjustAmount(side, order, true)
      manager.askMap mustEqual SortedMap(100.0 -> 10)
    }

    "calculate the right amount for new sell orders with takeLimit greater than quantity*price" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 10, price = Some(100.0), takeLimit = Some(1500))
      manager.adjustAmount(side, order, true)
      manager.askMap mustEqual SortedMap(100.0 -> 10)
    }

    "calculate the right amount for new sell orders with takeLimit less than quantity*price" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 10, price = Some(100.0), takeLimit = Some(500))
      manager.adjustAmount(side, order, true)
      manager.askMap mustEqual SortedMap(100.0 -> 5)
    }

    "calculate the right amount for new sell orders with takeLimit equals quantity*price" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 10, price = Some(100.0), takeLimit = Some(1000))
      manager.adjustAmount(side, order, true)
      manager.askMap mustEqual SortedMap(100.0 -> 10)
    }
  }

  "MarketDepthManager" should {

    "NOT calculate change bidMap for market price buy orders" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 1000, price = None)
      manager.adjustAmount(side.reverse, order, true)
      manager.bidMap.isEmpty mustEqual true
    }

    "calculate the right amount for new buy orders without takeLimit" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 1000, price = Some(1 / 100.0))
      manager.adjustAmount(side.reverse, order, true)
      manager.bidMap mustEqual SortedMap(1 / 100.0 -> 10)
    }

    "calculate the right amount for new sell orders with takeLimit greater than quantity*price" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 1000, price = Some(1 / 100.0), takeLimit = Some(15))
      manager.adjustAmount(side.reverse, order, true)
      manager.bidMap mustEqual SortedMap(1 / 100.0 -> 10)
    }

    "calculate the right amount for new sell orders with takeLimit less than quantity*price" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 1000, price = Some(1 / 100.0), takeLimit = Some(5))
      manager.adjustAmount(side.reverse, order, true)
      manager.bidMap mustEqual SortedMap(1 / 100.0 -> 5)
    }

    "calculate the right amount for new sell orders with takeLimit equals quantity*price" in {
      val manager = new MarketDepthManager(side)
      val order = Order(userId = 1L, id = 2L, quantity = 1000, price = Some(1 / 100.0), takeLimit = Some(10))
      manager.adjustAmount(side.reverse, order, true)
      manager.bidMap mustEqual SortedMap(1 / 100.0 -> 10)
    }
  }
}