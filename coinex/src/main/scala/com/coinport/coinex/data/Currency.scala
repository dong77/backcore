/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

sealed trait Currency {
  def ~>(another: Currency) = MarketSide(this, another)
  def <~(another: Currency) = MarketSide(another, this)
}

sealed trait FiatCurrency extends Currency
sealed trait EncryptedCurrency extends Currency

case object RMB extends FiatCurrency
case object USD extends FiatCurrency

case object BTC extends EncryptedCurrency
case object LTC extends EncryptedCurrency
case object PTS extends EncryptedCurrency
case object BTS extends EncryptedCurrency