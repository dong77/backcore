/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.{ MarketSide, Currency }
import java.math.MathContext

object CurrencyConversion {
  // exponent (10-based) of the factor between internal value and external value
  // Btc -> 3 means: 1 BTC(external value) equals 1 * 10E3 MBTC(internal value)
  val exponents = Map[Currency, Double](
    Btc -> 3,
    Ltc -> 3,
    Pts -> 3,
    Rmb -> 2,
    Usd -> 2
  )

  val factors: Map[Currency, Double] = exponents map {
    case (k, v) =>
      k -> math.pow(10, v)
  }
}

class CurrencyWrapper(val value: Double) {
  def externalValue(currency: Currency): Double = {
    value / CurrencyConversion.factors(currency)
  }

  def internalValue(currency: Currency): Long = {
    (value * CurrencyConversion.factors(currency)).toLong
  }
}

class PriceWrapper(val value: Double) {
  val mathContext = MathContext.UNLIMITED

  def inverse = (BigDecimal(1.0) / BigDecimal(value)).round(mathContext).doubleValue

  def externalValue(marketSide: MarketSide): Double = {
    val subjectFactor = CurrencyConversion.factors(marketSide._1)
    val currencyFactor = CurrencyConversion.factors(marketSide._2)
    val bigValue = BigDecimal(value)
    (bigValue * subjectFactor / currencyFactor).round(mathContext).doubleValue
  }

  def internalValue(marketSide: MarketSide): Double = {
    val subjectFactor = CurrencyConversion.factors(marketSide._1)
    val currencyFactor = CurrencyConversion.factors(marketSide._2)
    val bigValue = BigDecimal(value)
    (bigValue * currencyFactor / subjectFactor).round(mathContext).doubleValue
  }
}