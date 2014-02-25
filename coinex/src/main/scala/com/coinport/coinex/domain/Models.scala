package com.coinport.coinex.domain.models
import scala.collection.immutable.SortedSet

// Currency ------------------------
sealed trait Currency
sealed trait FiatCurrency extends Currency
sealed trait EncryptedCurrency extends Currency

case object RMB extends FiatCurrency
case object USD extends FiatCurrency

case object BTC extends EncryptedCurrency
case object LTC extends EncryptedCurrency
case object PTS extends EncryptedCurrency
case object BTS extends EncryptedCurrency

// Market Side ----------------------

case class MarketSide(out: Currency, in: Currency) {
  lazy val reverse = MarketSide(in, out)
  override def toString = "(%s/%s)".format(out, in)
}

// Order --------------------------
case class OrderData(id: Long, amount: Double, price: Double = 0)
case class Order(side: MarketSide, data: OrderData, created: Long = System.currentTimeMillis)

sealed trait OrderCondition {
  def eval: Boolean
}

case class ConditionalOrder(condition: OrderCondition, order: Order)

// Market Side ----------------------
object Market {
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
  type OrderPools = Map[MarketSide, Market.OrderPool]

  val EmptyOrderPool = SortedSet.empty[OrderData]
  val EmptyOrderPools = Map.empty[MarketSide, Market.OrderPool]
}

import Market._
case class Market(currency1: Currency, currency2: Currency,
  marketPriceOrderPools: Market.OrderPools = Market.EmptyOrderPools,
  limitPriceOrderPools: Market.OrderPools = Market.EmptyOrderPools,
  orderMap: Map[Long, OrderData] = Map.empty) {

  val side1 = MarketSide(currency1, currency2)
  val side2 = side1.reverse
  val sides = Seq(side1, side2)

  def getMarketPriceOrderPool(side: MarketSide): OrderPool = try {
    marketPriceOrderPools.getOrElse(side, Market.EmptyOrderPool)
  }

  def getLimitPriceOrderPool(side: MarketSide): OrderPool = try {
    limitPriceOrderPools.getOrElse(side, Market.EmptyOrderPool)
  }

  def addOrder(order: Order): Market = {
    val o = validateOrder(order)
    val data = o.data
    val market = removeOrder(data.id)

    val side = o.side
    var mpos = market.marketPriceOrderPools
    var lpos = market.limitPriceOrderPools
    if (data.price <= 0) {
      mpos = mpos + (side -> (market.getMarketPriceOrderPool(side) + data))
    } else {
      lpos = lpos + (side -> (market.getLimitPriceOrderPool(side) + data))
    }
    val orders = market.orderMap + (data.id -> data)

    market.copy(marketPriceOrderPools = mpos, limitPriceOrderPools = lpos, orderMap = orders)
  }

  def removeOrder(id: Long): Market = {
    orderMap.get(id) match {
      case Some(old) =>
        var mpos = marketPriceOrderPools
        var lpos = limitPriceOrderPools

        sides.foreach { side =>
          var pool = getMarketPriceOrderPool(side) - old
          if (pool.isEmpty) mpos = mpos - side
          else mpos = mpos + (side -> pool)

          pool = getLimitPriceOrderPool(side) - old
          if (pool.isEmpty) lpos = lpos - side
          else lpos = lpos + (side -> pool)
        }
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