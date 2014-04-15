/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import com.coinport.coinex.common._

class RichCurrency(raw: Currency) {
  def ~>(another: Currency) = MarketSide(raw, another)
  def <~(another: Currency) = MarketSide(another, raw)
}

class RichMarketSide(raw: MarketSide) {
  def reverse = MarketSide(raw.inCurrency, raw.outCurrency)
  def S = "%s%s".format(raw.outCurrency, raw.inCurrency).toUpperCase
  def s = "%s%s".format(raw.outCurrency, raw.inCurrency).toLowerCase
  def market = Market(raw.outCurrency, raw.inCurrency)
  def ordered = raw.inCurrency.getValue < raw.outCurrency.getValue
}

class RichOrder(raw: Order) {
  def inversePrice: Order = raw.price match {
    case Some(p) if p > 0 => raw.copy(price = Some(1 / p))
    case _ => raw
  }

  def vprice = raw.price.getOrElse(.0)

  def maxOutAmount(price: Double): Long = raw.takeLimit match {
    case Some(limit) if limit / price < raw.quantity => Math.ceil(limit / price).toLong
    case _ => raw.quantity
  }

  def maxInAmount(price: Double): Long = raw.takeLimit match {
    case Some(limit) if limit < raw.quantity * price => limit
    case _ => (raw.quantity * price).toLong
  }

  def hitTakeLimit = raw.takeLimit != None && raw.takeLimit.get <= 0

  def soldOut = if (raw.price != None) raw.quantity * vprice < 1 else raw.quantity == 0

  def isDust = raw.price != None && raw.quantity != 0 && raw.quantity * vprice < 1

  def isFullyExecuted: Boolean = soldOut || hitTakeLimit

  def -->(another: Order) = OrderUpdate(raw, another)
}

class RichOrderInfo(raw: OrderInfo) {
  def remainingQuantity = raw.order.quantity - raw.outAmount
}

class RichOrderUpdate(raw: OrderUpdate) {
  def userId = raw.previous.userId
  def id = raw.previous.id
  def price = raw.previous.price
  def outAmount = raw.previous.quantity - raw.current.quantity
}

class RichTransaction(raw: Transaction) {

}

class RichOrderSubmitted(raw: OrderSubmitted) {
  def hasTransaction = raw.txs != null && raw.txs.nonEmpty
}

class RichCashAccount(raw: CashAccount) {
  def total: Long = raw.available + raw.locked + raw.pendingWithdrawal

  def +(another: CashAccount): CashAccount = {
    if (raw.currency != another.currency)
      throw new IllegalArgumentException("Cannot add different currency accounts")
    CashAccount(raw.currency,
      raw.available + another.available,
      raw.locked + another.locked,
      raw.pendingWithdrawal + another.pendingWithdrawal)
  }

  def -(another: CashAccount): CashAccount = {
    if (raw.currency != another.currency)
      throw new IllegalArgumentException("Cannot minus different currency accounts")
    CashAccount(raw.currency,
      raw.available - another.available,
      raw.locked - another.locked,
      raw.pendingWithdrawal - another.pendingWithdrawal)
  }

  def isValid = (raw.available >= 0 && raw.locked >= 0 && raw.pendingWithdrawal >= 0)
}

class RichCandleDataItem(raw: CandleDataItem) {
  def mergeTo(another: CandleDataItem) =
    CandleDataItem(
      another.timestamp,
      raw.inAoumt + another.inAoumt,
      raw.outAoumt + another.outAoumt,
      another.open,
      raw.close,
      Math.min(raw.low, another.low),
      Math.max(raw.high, another.high))
}

class RichConstRole(v: ConstantRole.Value) {
  def << = v.toString.toLowerCase
}

class RichMarketRole(v: MarketRole.Value) {
  def <<(side: MarketSide) = v.toString.toLowerCase + "_" + new RichMarketSide(side).s
}

class RichPersistentId(v: PersistentId.Value) {
  def << : String = v.toString.toLowerCase
  def <<(side: MarketSide): String = << + "_" + new RichMarketSide(side).s
}

object Implicits {
  implicit def currency2Rich(raw: Currency) = new RichCurrency(raw)
  implicit def marketSide2Rich(raw: MarketSide) = new RichMarketSide(raw)
  implicit def order2Rich(raw: Order) = new RichOrder(raw)
  implicit def orderInfo2Rich(raw: OrderInfo) = new RichOrderInfo(raw)
  implicit def orderUpdate2Rich(raw: OrderUpdate) = new RichOrderUpdate(raw)
  implicit def transaction2Rich(raw: Transaction) = new RichTransaction(raw)
  implicit def orderSubmitted2Rich(raw: OrderSubmitted) = new RichOrderSubmitted(raw)
  implicit def cashAccont2Rich(raw: CashAccount) = new RichCashAccount(raw)
  implicit def candleDataItem2Rich(raw: CandleDataItem) = new RichCandleDataItem(raw)

  implicit def constantRole2Rich(raw: ConstantRole.Value) = new RichConstRole(raw)
  implicit def marketRole2Rich(raw: MarketRole.Value) = new RichMarketRole(raw)
  implicit def persistentId2Rich(raw: PersistentId.Value) = new RichPersistentId(raw)

  implicit def string2RichMarketSide(raw: String): MarketSide = {
    if (raw == null || raw.isEmpty || raw.length < 6) {
      MarketSide(Currency.Unknown, Currency.Unknown)
    } else {
      val left = Currency.valueOf(raw.substring(0, 3).toLowerCase.capitalize).getOrElse(Currency.Unknown)
      val right = Currency.valueOf(raw.substring(3, 6).toLowerCase.capitalize).getOrElse(Currency.Unknown)
      MarketSide(left, right)
    }
  }
  implicit def string2RichMarket(raw: String): Market = string2RichMarketSide(raw).market
}
