/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.data.mutable

import scala.collection.mutable.Map
import scala.collection.mutable.SortedSet

import com.coinport.coinex.data._
import Implicits._
import MarketState._

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

  def apply(tms: TMarketState): MarketState = MarketState(
    tms.side,
    Map.empty[MarketSide, MarketState.OrderPool] ++ tms.orderPools.map(
      item => (item._1 -> (SortedSet.empty[Order] ++ item._2))),
    Map.empty[Long, Order] ++ tms.orderMap,
    tms.priceRestriction
  )
}

case class MarketState(
    headSide: MarketSide,
    orderPools: MarketState.OrderPools = Map.empty,
    orderMap: Map[Long, Order] = Map.empty,
    // the price restriction of market price which avoiding user mis-type a price (5000 -> 500)
    var priceRestriction: Option[Double] = None) {

  val tailSide = headSide.reverse
  val bothSides = Seq(headSide, tailSide)

  def bestHeadSidePrice = orderPool(headSide).headOption.map(_.price)
  def bestTailSidePrice = orderPool(tailSide).headOption.map(_.price)

  def orderPool(side: MarketSide): OrderPool = {
    orderPools.getOrElse(side, SortedSet.empty[Order])
  }

  def setPriceRestriction(value: Option[Double]) = {
    priceRestriction = value
  }

  def popOrder(side: MarketSide): (Option[Order], MarketState) = {
    orderPool(side).headOption match {
      case None => (None, this)
      case order => (order, removeOrder(side, order.get.id))
    }
  }

  def addOrder(side: MarketSide, order: Order): MarketState = {
    order.price match {
      case None =>
        this
      case p =>
        removeOrder(side, order.id)
        if (orderPools.contains(side)) {
          orderPool(side) += order
        } else {
          orderPools += (side -> (orderPool(side) + order))
        }
        orderMap += (order.id -> order)
        this
    }
  }

  def getOrder(id: Long): Option[Order] = orderMap.get(id)

  def removeOrder(side: MarketSide, id: Long): MarketState = {
    orderMap.get(id) match {
      case Some(order) if orderPool(side).contains(order) =>
        orderPool(side) -= order
        if (orderPool(side).isEmpty) orderPools -= side
        // else orderPools += (side -> orderPool(side))
        orderMap -= id
        this

      case _ =>
        this
    }
  }

  def copy = MarketState(headSide, orderPools.map(item => (item._1 -> item._2.clone)), orderMap.clone, priceRestriction)

  def toThrift = TMarketState(
    headSide, orderPools.map(item => (item._1 -> item._2.toList)), orderMap.clone, priceRestriction)
}
