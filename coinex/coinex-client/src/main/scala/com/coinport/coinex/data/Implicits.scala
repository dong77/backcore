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
  def asString = "%s_%s".format(raw.outCurrency, raw.inCurrency).toLowerCase
}

class RichOrder(raw: Order) {
  def inversePrice: Order = raw.price match {
    case Some(p) if p > 0 => raw.copy(price = Some(1 / p))
    case _ => raw
  }

  def vprice = raw.price.getOrElse(.0)

  def maxOutAmount(price: Double): Long = raw.takeLimit match {
    case Some(limit) if limit / price < raw.quantity => Math.round(limit / price)
    case _ => raw.quantity
  }

  def maxInAmount(price: Double): Long = raw.takeLimit match {
    case Some(limit) if limit < raw.quantity * price => limit
    case _ => Math.round(raw.quantity * price)
  }

  def hitTakeLimit = raw.takeLimit == Some(0)

  def isFullyExecuted: Boolean = raw.quantity == 0 || hitTakeLimit

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
      raw.volumn + another.volumn,
      another.open,
      raw.close,
      Math.min(raw.low, another.low),
      Math.max(raw.high, another.high))
}

class RichConstRole(v: ConstantRole.Value) {
  def << = v.toString.toLowerCase
}

class RichMarketRole(v: MarketRole.Value) {
  def <<(side: MarketSide) = v.toString.toLowerCase + "_" + new RichMarketSide(side).asString
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

  implicit def constantRole2Rich(r: ConstantRole.Value) = new RichConstRole(r)
  implicit def marketRole2Rich(r: MarketRole.Value) = new RichMarketRole(r)
}
