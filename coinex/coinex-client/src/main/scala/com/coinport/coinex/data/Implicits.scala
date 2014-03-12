/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data


class RichCurrency(raw: Currency) {
  def ~>(another: Currency) = MarketSide(raw, another)
  def <~(another: Currency) = MarketSide(another, raw)
}

class RichMarketSide(raw: MarketSide) {
  def reverse = MarketSide(raw.inCurrency, raw.outCurrency)
  def asString = "%s_%s".format(raw.outCurrency, raw.inCurrency).toLowerCase
}

class RichPrice(raw: Price) {
  def reverse = Price(new RichMarketSide(raw.side).reverse, 1 / raw.price)
}

class RichOrder(raw: Order) {
  def inversePrice: Order = raw.price match {
    case Some(p) if p > 0 => raw.copy(price = Some(1 / p))
    case _ => raw
  }

  def vprice = raw.price.getOrElse(.0)
}

class RichTransaction(raw: Transaction) {
  lazy val takerPrice = raw.maker.quantity.toDouble / raw.taker.quantity
  lazy val makerPrice = raw.taker.quantity.toDouble / raw.maker.quantity
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

object Implicits {
  implicit def currency2Rich(raw: Currency) = new RichCurrency(raw)
  implicit def marketSide2Rich(raw: MarketSide) = new RichMarketSide(raw)
  implicit def price2Rich(raw: Price) = new RichPrice(raw)
  implicit def order2Rich(raw: Order) = new RichOrder(raw)
  implicit def transaction2Rich(raw: Transaction) = new RichTransaction(raw)
  implicit def cashAccont2Rich(raw: CashAccount) = new RichCashAccount(raw)
}