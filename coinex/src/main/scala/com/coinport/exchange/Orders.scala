package com.coinport.exchange

import CurrencyType._

case class Deposit(id: Long, uid: Long, currency: CurrencyType, total: Double)

sealed trait Withdrawl
case class FiatCurrencyWithdrawl(currency: CurrencyType, total: Double)
case class VirtualCurrencyWithdrawl(currency: CurrencyType, total: Double, address: String)

object OrderStatus extends Enumeration {
  type OrderStatus = Value
  val Pending, Cancelled, PartiallyExecuted, FullyExecuted = Value
}

import OrderStatus._

sealed trait Order
case class LimitPriceBuyOrder(id: Long = -1, uid: Long = -1, market: Market, totalCost: Double, price: Double, status: OrderStatus = Pending) extends Order
case class LimitPriceSellOrder(id: Long = -1, uid: Long = -1, market: Market, volume: Double, price: Double, status: OrderStatus = Pending) extends Order

case class MarketPriceBuyOrder(id: Long = -1, uid: Long = -1, market: Market, totalCost: Double, status: OrderStatus = Pending) extends Order
case class MarketPriceSellOrder(id: Long = -1, uid: Long = -1, market: Market, volume: Double, status: OrderStatus = Pending) extends Order