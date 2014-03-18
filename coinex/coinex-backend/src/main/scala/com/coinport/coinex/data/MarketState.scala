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
    orderPools: MarketState.OrderPools = MarketState.EmptyOrderPools,
    orderMap: Map[Long, Order] = Map.empty,
    // the price restriction of market price which avoiding user mis-type a price (5000 -> 500)
    priceRestriction: Option[Double] = None) {

  val tailSide = headSide.reverse
  val bothSides = Seq(headSide, tailSide)

  def bestHeadSidePrice = orderPool(headSide).headOption.map(_.price)
  def bestTailSidePrice = orderPool(tailSide).headOption.map(_.price)

  def orderPool(side: MarketSide): OrderPool = {
    orderPools.getOrElse(side, MarketState.EmptyOrderPool)
  }

  def setPriceRestriction(value: Option[Double]) = {
    copy(priceRestriction = value)
  }

  def addOrder(side: MarketSide, order: Order): MarketState = {
    order.price match {
      case None =>
        this
      case p =>
        val market = removeOrder(side, order.id)

        var lpos = market.orderPools

        lpos += (side -> (market.orderPool(side) + order))
        val orders = market.orderMap + (order.id -> order)

        market.copy(orderPools = lpos, orderMap = orders)
    }
  }

  def getOrder(id: Long): Option[Order] = orderMap.get(id)

  def removeOrder(side: MarketSide, id: Long): MarketState = {
    orderMap.get(id) match {
      case Some(order) if orderPool(side).contains(order) =>
        var lpos = orderPools

        var pool = orderPool(side) - order
        if (pool.isEmpty) lpos -= side
        else lpos += (side -> pool)

        val orders = orderMap - id
        copy(orderPools = lpos, orderMap = orders)

      case _ =>
        this
    }
  }
}
