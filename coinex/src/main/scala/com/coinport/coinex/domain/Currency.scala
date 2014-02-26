package com.coinport.coinex.domain

/**
 * ATTENTION PLEASE:
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that we snapshot is taken and
 * persistent, the program can still update the live state.
 *
 */

sealed trait Currency {
  def ~(another: Currency) = MarketSide(this, another)
}

sealed trait FiatCurrency extends Currency
sealed trait EncryptedCurrency extends Currency

case object RMB extends FiatCurrency
case object USD extends FiatCurrency

case object BTC extends EncryptedCurrency
case object LTC extends EncryptedCurrency
case object PTS extends EncryptedCurrency
case object BTS extends EncryptedCurrency