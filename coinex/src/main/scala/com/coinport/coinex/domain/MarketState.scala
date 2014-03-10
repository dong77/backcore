/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.domain

import scala.collection.immutable.SortedSet
import Implicits._

object MarketState {
  implicit val ordering = new Ordering[OrderData] {
    def compare(a: OrderData, b: OrderData) = {
      if (a.price < b.price) -1
      else if (a.price > b.price) 1
      else if (a.id < b.id) -1
      else if (a.id > b.id) 1
      else 0
    }
  }

  type OrderPool = SortedSet[OrderData]
  type OrderPools = Map[MarketSide, MarketState.OrderPool]

  val EmptyOrderPool = SortedSet.empty[OrderData]
  val EmptyOrderPools = Map.empty[MarketSide, MarketState.OrderPool]
}

import MarketState._
import Implicits._

/**
 * This class is the real in-memory state (data model) for a event-sourcing market processor.
 * It should be kept as a case class with immutable collections.
 */
case class MarketState(
  headSide: MarketSide,
  marketPriceOrderPools: MarketState.OrderPools = MarketState.EmptyOrderPools,
  limitPriceOrderPools: MarketState.OrderPools = MarketState.EmptyOrderPools,
  orderMap: Map[Long, Order] = Map.empty) {

  val tailSide = headSide.reverse
  val bothSides = Seq(headSide, tailSide)

  def bestHeadSidePrice = limitPriceOrderPool(headSide).headOption.map(_.price)
  def bestTailSidePrice = limitPriceOrderPool(tailSide).headOption.map(_.price)

  def marketPriceOrderPool(side: MarketSide): OrderPool = {
    marketPriceOrderPools.getOrElse(side, MarketState.EmptyOrderPool)
  }

  def limitPriceOrderPool(side: MarketSide): OrderPool = {
    limitPriceOrderPools.getOrElse(side, MarketState.EmptyOrderPool)
  }

  def addOrder(order: Order): MarketState = {
    val validated = validateOrder(order)
    val data = validated.data
    val side = validated.side
    val market = removeOrder(data.id)

    var mpos = market.marketPriceOrderPools
    var lpos = market.limitPriceOrderPools

    if (data.price <= 0) {
      mpos += (side -> (market.marketPriceOrderPool(side) + data))
    } else {
      lpos += (side -> (market.limitPriceOrderPool(side) + data))
    }
    val orders = market.orderMap + (data.id -> validated)

    market.copy(marketPriceOrderPools = mpos, limitPriceOrderPools = lpos, orderMap = orders)
  }

  def removeOrder(id: Long): MarketState = {
    orderMap.get(id) match {
      case Some(old) =>
        var mpos = marketPriceOrderPools
        var lpos = limitPriceOrderPools

        var pool = marketPriceOrderPool(old.side) - old.data
        if (pool.isEmpty) mpos -= old.side
        else mpos += (old.side -> pool)

        pool = limitPriceOrderPool(old.side) - old.data
        if (pool.isEmpty) lpos -= old.side
        else lpos += (old.side -> pool)

        val orders = orderMap - id
        copy(marketPriceOrderPools = mpos, limitPriceOrderPools = lpos, orderMap = orders)

      case None =>
        this
    }
  }

  def validateOrder(order: Order): Order = {
    val data = if (order.data.price >= 0) order.data else order.data.copy(price = 0)
    order.copy(data = data)
  }
}
