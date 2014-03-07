/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.exchange

import scala.collection.immutable.SortedSet

case class MarketSide(/* from */ outCurrency: Currency, /* to */inCurrency: Currency) {
  def reverse = inCurrency ~> outCurrency
  override def toString = "%s_%s".format(outCurrency, inCurrency).toLowerCase
}

class OrderComparator(isDescend: Boolean) extends Ordering[OrderData] {
    override def compare(a: OrderData, b: OrderData) = {
      val priceDiff = (a.price - b.price) * (if (isDescend) -1 else 1)
      if (priceDiff < 0) -1
      else if (priceDiff > 0) 1
      else if (a.timestamp < b.timestamp) -1
      else if (a.timestamp > b.timestamp) 1
      else if (a.id < b.id) -1
      else if (a.id > b.id) 1
      else 0
    }
}

import BuyOrSell._
import MarketOrLimit._

object MarketState {
  val ascendSort = new OrderComparator(false)
  val descendSort = new OrderComparator(true)

  type OrderPool = SortedSet[OrderData]

  def getComparator(buyOrSell: BuyOrSell) = if (buyOrSell == BUY) descendSort else ascendSort
}

import MarketState._

case class MarketState(
  marketSide: MarketSide,
  orderPools: Map[MarketOrLimit, Map[BuyOrSell, SortedSet[OrderData]]] = Map.empty,
  orderMap: Map[Long, Order] = Map.empty) {


  def bestBuyPrice = getOrderPool(LIMIT, BUY).headOption.map(_.price)
  def bestSellPrice = getOrderPool(LIMIT, SELL).headOption.map(_.price)

  def getOrderPool(marketOrLimit: MarketOrLimit, buyOrSell: BuyOrSell): OrderPool = {
    // TODO(c) extract this function
    implicit val sort = getComparator(buyOrSell)
    orderPools.getOrElse(marketOrLimit, Map.empty[BuyOrSell, SortedSet[OrderData]])
      .getOrElse(buyOrSell, SortedSet.empty[OrderData])
  }

  def addOrder(order: Order): MarketState = {
    val validated = validateOrder(order)
    val data = validated.data
    val marketOrLimit = data.marketOrLimit
    val buyOrSell = data.buyOrSell

    val market = removeOrder(data.id)

    implicit val sort = getComparator(buyOrSell)
    var buyOrSellPools = market.orderPools.getOrElse(marketOrLimit, Map.empty[BuyOrSell, SortedSet[OrderData]])
    var orderPool = buyOrSellPools.getOrElse(buyOrSell, SortedSet.empty[OrderData]) + data
    buyOrSellPools += (buyOrSell -> orderPool)
    var newOrderPools = orderPools + (marketOrLimit -> buyOrSellPools)
    val orders = orderMap + (data.id -> validated)

    copy(orderPools = newOrderPools, orderMap = orders)
  }

  def removeOrder(id: Long): MarketState = {
    orderMap.get(id) match {
      case Some(old) =>
        val marketOrLimit = old.data.marketOrLimit
        val buyOrSell = old.data.buyOrSell
        var buyOrSellPools = orderPools.get(marketOrLimit).get;
        val orderPool = buyOrSellPools.get(buyOrSell).get - old.data
        var newOrderPools = orderPools
        if (orderPool.isEmpty) {
          buyOrSellPools -= buyOrSell
          if (buyOrSellPools.isEmpty)
            newOrderPools -= marketOrLimit
          else
            newOrderPools += (marketOrLimit -> buyOrSellPools)
        } else {
          buyOrSellPools += (buyOrSell -> (orderPool))
          newOrderPools += (marketOrLimit -> buyOrSellPools)
        }
        copy(orderPools = newOrderPools, orderMap = orderMap - id)
      case _ =>
        MarketState.this
    }
  }

  def validateOrder(order: Order): Order = {
    val data = if (order.data.price >= 0) order.data else order.data.copy(price = 0)
    order.copy(data = data)
  }

  override def toString = "SIDE: %s\nORDERS: %s\nHANGING: %s\n".format(
    marketSide, orderMap, orderPools)
}
