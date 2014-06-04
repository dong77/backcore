/**
 * Copyright (C) 2014 Coinport Inc.
 */
package com.coinport.coinex.api.model

import org.specs2.mutable._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.data.Currency._

class CurrencyConversionTest extends Specification {
  "currency conversions" should {
    "String to Currency" in {
      val currency1: Currency = "CNY"
      val currency2: Currency = "BTC"
      val currency3: Currency = "Cny"
      val currency4: Currency = "cny"
      val currency5: Currency = "USD"
      val currency6: Currency = "XXC"

      currency1 mustEqual Currency.Cny
      currency2 mustEqual Currency.Btc
      currency1 mustEqual currency3
      currency1 mustEqual currency4
      currency5 mustEqual Currency.Usd
      currency6 mustEqual Currency.Unknown
    }

    "currency unit conversion" in {
      12.345.internalValue(Btc) mustEqual 1234500000L
      23.45.internalValue(Cny) mustEqual 2345000L

      100000000L.externalValue(Btc) mustEqual 1.0
      78900000L.externalValue(Cny) mustEqual 789.0

      3456.0.internalValue(Btc ~> Cny) mustEqual 3.456
      3.456.externalValue(Btc ~> Cny) mustEqual 3456.0
    }

    "very small number" in {
      20.00097.internalValue(Dog) mustEqual 2000097000L
      0.000000123.internalValue(Dog ~> Btc) mustEqual 0.000000123

      2000097000L.externalValue(Dog) mustEqual 20.00097
      0.000000123.externalValue(Dog ~> Btc) mustEqual 0.000000123
    }

    "very big number" in {
      1234567820.00097.internalValue(Dog) mustEqual 123456782000097000L
      123456782000097000L.externalValue(Dog) mustEqual 1234567820.00097
    }
  }
}