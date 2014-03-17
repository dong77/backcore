/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import scala.collection.immutable.SortedSet
import MarketState._
import Implicits._

object MarketState {
  implicit val ordering = new Ordering[Order] {
    def compare(a: Order, b: Order) = {
      if (a.vprice < b.vprice) -1
      else if (a.vprice > b.vprice) 1
      else if (a.id < b.id) -1
      else if (a.id > b.id) 1
      else 0
    }
  }

  type OrderPool = SortedSet[Order]
  type OrderPools = Map[MarketSide, MarketState.OrderPool]

  val EmptyOrderPool = SortedSet.empty[Order]
  val EmptyOrderPools = Map.empty[MarketSide, MarketState.OrderPool]
}

/**
 * This class is the real in-memory state (data model) for a event-sourcing market processor.
 * It should be kept as a case class with immutable collections.
 */
case class MarketState(
  headSide: MarketSide,
  marketPriceOrderPools: MarketState.OrderPools = MarketState.EmptyOrderPools,
  limitPriceOrderPools: MarketState.OrderPools = MarketState.EmptyOrderPools,
  orderMap: Map[Long, Order] = Map.empty,
  priceRestriction: Option[Double] = None) {

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

  def setPriceRestriction(value: Option[Double]) = {
    copy(priceRestriction = value)
  }

  def addOrder(side: MarketSide, order: Order): MarketState = {
    val market = removeOrder(side, order.id)

    var mpos = market.marketPriceOrderPools
    var lpos = market.limitPriceOrderPools

    order.price match {
      case Some(p) if p > 0 =>
        lpos += (side -> (market.limitPriceOrderPool(side) + order))
      case _ =>
        mpos += (side -> (market.marketPriceOrderPool(side) + order))
    }
    val orders = market.orderMap + (order.id -> order)

    market.copy(marketPriceOrderPools = mpos, limitPriceOrderPools = lpos, orderMap = orders)
  }

  def getOrder(side: MarketSide,id: Long): Option[Order] =  orderMap.get(id)

  def removeOrder(side: MarketSide, id: Long): MarketState = {
    orderMap.get(id) match {
      case Some(order) if marketPriceOrderPool(side).contains(order) || limitPriceOrderPool(side).contains(order) =>
        var mpos = marketPriceOrderPools
        var lpos = limitPriceOrderPools

        var pool = marketPriceOrderPool(side) - order
        if (pool.isEmpty) mpos -= side
        else mpos += (side -> pool)

        pool = limitPriceOrderPool(side) - order
        if (pool.isEmpty) lpos -= side
        else lpos += (side -> pool)

        val orders = orderMap - id
        copy(marketPriceOrderPools = mpos, limitPriceOrderPools = lpos, orderMap = orders)

      case _ =>
        this
    }
  }
}
