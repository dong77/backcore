/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.{ MarketSide, Currency }

object CurrencyConversion {
  // exponent (10-based) of the factor between internal value and external value
  // Btc -> 3 means: 1 BTC(external value) equals 1 * 10E3 MBTC(internal value)
  val exponents = Map[Currency, Double](
    Btc -> 3,
    Ltc -> 3,
    Pts -> 3,
    Dog -> 3,
    Cny -> 2,
    Usd -> 2
  )

  val multipliers: Map[Currency, Double] = exponents map {
    case (k, v) =>
      k -> math.pow(10, v)
  }

  def getExponent(currency: Currency) = exponents.get(currency).getOrElse(1.0).toInt

  def getMultiplier(currency: Currency) = multipliers.get(currency).getOrElse(1.0)
}

class CurrencyWrapper(val value: Double) {
  def externalValue(currency: Currency): Double = {
    value / CurrencyConversion.multipliers(currency)
  }

  def internalValue(currency: Currency): Long = {
    (value * CurrencyConversion.multipliers(currency)).toLong
  }
}

class PriceWrapper(val value: Double) {
  def externalValue(marketSide: MarketSide): Double = {
    val subjectFactor = CurrencyConversion.multipliers(marketSide._1)
    val currencyFactor = CurrencyConversion.multipliers(marketSide._2)

    value * subjectFactor / currencyFactor
  }

  def internalValue(marketSide: MarketSide): Double = {
    val subjectFactor = CurrencyConversion.multipliers(marketSide._1)
    val currencyFactor = CurrencyConversion.multipliers(marketSide._2)

    value * currencyFactor / subjectFactor
  }
}