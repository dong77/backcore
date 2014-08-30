/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.markets

import org.specs2.mutable._
import scala.collection.immutable.SortedSet

import com.coinport.coinex.data._
// import com.coinport.coinex.debug.Debugger
import Implicits._
import Currency._
import OrderStatus._
import RefundReason._

class MarketManagerSpec2 extends Specification {
  val takerSide = Btc ~> Cny
  val makerSide = takerSide.reverse

  import MarketManager._

  "MarketManager" should {
    "control the max number of transactions per order" in {
      val maxNumOfTxPerOrder = 100
      val manager = new MarketManager(takerSide, maxNumOfTxPerOrder)

      (1 to maxNumOfTxPerOrder + 10) foreach { i =>
        val maker = Order(userId = i, id = i, price = Some(1 reciprocal), quantity = 1)
        manager.addOrderToMarket(makerSide, maker)
      }

      val taker = Order(userId = 9000, id = 9000, price = Some(1), quantity = 1000000000L /* large enough */ )
      val result = manager.addOrderToMarket(takerSide, taker)

      result.txs.size mustEqual maxNumOfTxPerOrder
      manager.orderPool(makerSide).size mustEqual 10
      manager.orderPool(takerSide).size mustEqual 0
      //println(result.toString
      //.replace("Order(", "\n\t\t\tOrder(")
      //.replace("Transaction(", "\n\tTransaction(")
      //.replace("OrderUpdate(", "\n\t\tOrderUpdate("))
      result.originOrderInfo.status mustEqual OrderStatus.PartiallyExecutedThenCancelledByMarket
    }
  }
}
