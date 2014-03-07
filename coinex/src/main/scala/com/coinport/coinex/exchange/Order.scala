/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.exchange

object BuyOrSell extends Enumeration {
  type BuyOrSell = Value
  val BUY, SELL = Value

  def reverse(value: BuyOrSell) = if (value == BUY) SELL else BUY
}

object MarketOrLimit extends Enumeration {
  type MarketOrLimit = Value
  val MARKET, LIMIT = Value
}

import BuyOrSell._
import MarketOrLimit._

case class OrderData(id: Long, uid: Long, buyOrSell: BuyOrSell = BUY, marketOrLimit: MarketOrLimit = LIMIT,
  quantity: Long = 1L, price: Long = 1L, timestamp: Long = 0L)

case class Order(side: MarketSide, data: OrderData)
