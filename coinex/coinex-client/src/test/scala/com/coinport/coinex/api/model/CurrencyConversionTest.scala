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
      val currency1: Currency = "RMB"
      val currency2: Currency = "BTC"
      val currency3: Currency = "Rmb"
      val currency4: Currency = "cny"
      val currency5: Currency = "USD"
      val currency6: Currency = "XXC"

      currency1 mustEqual Currency.Rmb
      currency2 mustEqual Currency.Btc
      currency1 mustEqual currency3
      currency1 mustEqual currency4
      currency5 mustEqual Currency.Usd
      currency6 must beNull
    }

    "currency unit conversion" in {
      12.345.internalValue(Btc) mustEqual 12345L
      23.45.internalValue(Rmb) mustEqual 2345L

      1000.externalValue(Btc) mustEqual 1.0
      78900.externalValue(Rmb) mustEqual 789.0

      3456.0.internalValue(Btc ~> Rmb) mustEqual 345.6
      (1.0 / 3456.0).internalValue(Rmb ~> Btc) mustEqual (1.0 / 345.6)
      345.6.externalValue(Btc ~> Rmb) mustEqual 3456.0
      (1.0 / 345.6).externalValue(Rmb ~> Btc) mustEqual (1.0 / 3456.0)
    }
  }
}