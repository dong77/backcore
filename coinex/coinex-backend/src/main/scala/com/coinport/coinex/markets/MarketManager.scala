/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * MarketManager is the maintainer of a Market. It executes new orders before
 * they are added into a market as pending orders. As execution results, a list
 * of Transactions are generated and returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 *
 * Note this class does NOT depend on event-sourcing framework we choose. Please
 * keep it plain old scala/java.
 */

package com.coinport.coinex.markets

import scala.collection.mutable.Map
import scala.collection.mutable.SortedSet
import com.coinport.coinex.data._
import com.coinport.coinex.common.Manager
import com.coinport.coinex.common.RedeliverFilter
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import Implicits._
import OrderStatus._
import RefundReason._

object MarketManager {
  private[markets] implicit val ordering = new Ordering[Order] {
    def compare(a: Order, b: Order) = {
      if (a.vprice < b.vprice) -1
      else if (a.vprice > b.vprice) 1
      else if (a.id < b.id) -1
      else if (a.id > b.id) 1
      else 0
    }
  }
}

class MarketManager(headSide: MarketSide, initialLastOrderId: Long = 0L, initialLastTxId: Long = 0L) extends Manager[TMarketState] {
  private[markets] var lastOrderId = initialLastTxId
  private[markets] var lastTxId = initialLastTxId
  private[markets] val orderPools = Map.empty[MarketSide, SortedSet[Order]]
  private[markets] val orderMap = Map.empty[Long, Order]
  private[markets] var priceRestriction: Option[Double] = None

  private val tailSide = headSide.reverse
  private val bothSides = Seq(headSide, tailSide)

  import MarketManager._

  def addOrderToMarket(takerSide: MarketSide, raw: Order): OrderSubmitted = {
    val order = raw.copy(id = getOrderId)

    val txsBuffer = new ListBuffer[Transaction]

    val (totalOutAmount, totalInAmount, takerOrder) =
      addOrderToMarketRec(takerSide.reverse, takerSide, order, 0, 0, txsBuffer)

    val status =
      if (takerOrder.isFullyExecuted) OrderStatus.FullyExecuted
      else if (totalOutAmount > 0) {
        if (takerOrder.price == None || takerOrder.onlyTaker.getOrElse(false)) OrderStatus.PartiallyExecutedThenCancelledByMarket
        else OrderStatus.PartiallyExecuted
      } else if (takerOrder.price == None || takerOrder.onlyTaker.getOrElse(false)) OrderStatus.CancelledByMarket
      else OrderStatus.Pending

    val refundReason: Option[RefundReason] =
      if (takerOrder.quantity == 0) None
      else if (takerOrder.hitTakeLimit) Some(HitTakeLimit)
      else if (takerOrder.isDust) Some(Dust)
      else if (status == PartiallyExecutedThenCancelledByMarket || status == CancelledByMarket) Some(AutoCancelled)
      else None

    if (txsBuffer.size != 0 && refundReason != None) {
      val lastTx = txsBuffer.last
      txsBuffer.trimEnd(1)
      txsBuffer += lastTx.copy(takerUpdate = lastTx.takerUpdate.copy(current = lastTx.takerUpdate.current.copy(
        refundReason = refundReason)))
    }

    val txs = txsBuffer.toSeq
    val orderInfo = OrderInfo(takerSide,
      if (txs.size == 0) order.copy(refundReason = refundReason) else order,
      totalOutAmount, totalInAmount, status, txs.lastOption.map(_.timestamp))

    OrderSubmitted(orderInfo, txs)
  }

  @tailrec
  private final def addOrderToMarketRec(makerSide: MarketSide, takerSide: MarketSide, takerOrder: Order,
    totalOutAmount: Long, totalInAmount: Long, txsBuffer: ListBuffer[Transaction]): ( /*totalOutAmount*/ Long, /*totalInAmount*/ Long, /*updatedTaker*/ Order) = {
    val makerOrderOption = orderPool(makerSide).headOption
    if (makerOrderOption == None || makerOrderOption.get.vprice * takerOrder.vprice > 1) {
      // Return point. Market-price order can not turn into a maker order
      if (!takerOrder.isFullyExecuted && takerOrder.price != None && !takerOrder.onlyTaker.getOrElse(false)) addOrder(takerSide, takerOrder)

      (totalOutAmount, totalInAmount, takerOrder)
    } else {
      val makerOrder = makerOrderOption.get
      val price = 1 / makerOrder.vprice
      val lvOutAmount = Math.min(takerOrder.maxOutAmount(price), makerOrder.maxInAmount(1 / price))

      if (lvOutAmount == 0) { // return point
        (totalOutAmount, totalInAmount, takerOrder)
      } else {
        val lvInAmount = Math.round(lvOutAmount * price)

        val updatedTaker = takerOrder.copy(quantity = takerOrder.quantity - lvOutAmount,
          takeLimit = takerOrder.takeLimit.map(_ - lvInAmount), inAmount = takerOrder.inAmount + lvInAmount)

        val updatedMaker = makerOrder.copy(quantity = makerOrder.quantity - lvInAmount,
          takeLimit = makerOrder.takeLimit.map(_ - lvOutAmount), inAmount = makerOrder.inAmount + lvOutAmount)

        val refundReason: Option[RefundReason] =
          if (updatedMaker.quantity == 0) None
          else if (updatedMaker.hitTakeLimit) Some(HitTakeLimit)
          else if (updatedMaker.isDust) Some(Dust)
          else None

        txsBuffer += Transaction(getTxId, takerOrder.timestamp.getOrElse(0), takerSide,
          takerOrder --> updatedTaker, makerOrder --> updatedMaker.copy(refundReason = refundReason))

        removeOrder(makerOrder.id)
        if (updatedMaker.isFullyExecuted) { // return point
          addOrderToMarketRec(makerSide, takerSide, updatedTaker, totalOutAmount + lvOutAmount, totalInAmount + lvInAmount, txsBuffer)
        } else { // return point
          addOrder(makerSide, updatedMaker)
          (totalOutAmount + lvOutAmount, totalInAmount + lvInAmount, updatedTaker)
        }
      }
    }
  }

  def getSnapshot = TMarketState(lastOrderId, lastTxId,
    orderPools.map(item => (item._1 -> item._2.toList)),
    orderMap.clone, priceRestriction, getFiltersSnapshot)

  def loadSnapshot(s: TMarketState) {
    lastOrderId = s.lastOrderId
    lastTxId = s.lastTxId
    orderPools.clear
    orderPools ++= s.orderPools.map(item => (item._1 -> (SortedSet.empty[Order] ++ item._2)))
    orderMap.clear
    orderMap ++= s.orderMap
    loadFiltersSnapshot(s.filters)
  }

  def isOrderPriceInGoodRange(takerSide: MarketSide, price: Option[Double]): Boolean = {
    if (price.isEmpty) true
    else if (price.get <= 0) false
    else if (priceRestriction.isEmpty || orderPool(takerSide).isEmpty) true
    else if (price.get / orderPool(takerSide).headOption.get.price.get - 1.0 <= priceRestriction.get) true
    else false
  }

  def addOrder(side: MarketSide, order: Order) = {
    assert(order.price.isDefined)
    orderPools += (side -> (orderPool(side) + order))
    orderMap += (order.id -> order)
  }

  def getOrderMarketSide(orderId: Long, userId: Long): Option[MarketSide] =
    orderMap.get(orderId) filter (_.userId == userId) map { order =>
      if (orderPool(tailSide).contains(order)) tailSide else headSide
    }

  def removeOrder(orderId: Long, userId: Long): (MarketSide, Order) = {
    val order = orderMap(orderId)
    assert(order.userId == userId)

    orderMap -= orderId
    if (orderPool(headSide).contains(order)) {
      orderPools += (headSide -> (orderPool(headSide) - order))
      (headSide, order)
    } else {
      orderPools += (tailSide -> (orderPool(tailSide) - order))
      (tailSide, order)
    }
  }
  private[markets] def orderPool(side: MarketSide) = orderPools.getOrElseUpdate(side, SortedSet.empty[Order])

  private def removeOrder(orderId: Long) = {
    val order = orderMap(orderId)
    orderMap -= orderId
    orderPools += (headSide -> (orderPool(headSide) - order))
    orderPools += (tailSide -> (orderPool(tailSide) - order))
  }

  private def getOrderId(): Long = {
    lastOrderId += 1
    lastOrderId
  }
  private def getTxId(): Long = {
    lastTxId += 1
    lastTxId
  }
}
