/**
 * Copyright (C) 2014 Coinport Inc.
 */
package com.coinport.coinex.api.model

import org.specs2.mutable._
import com.coinport.coinex.data._
import com.coinport.coinex.api.model.CurrencyUnits._

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
      val a = 1 unit BTC
      val b = 1000 unit MBTC

      a mustEqual b

      a / b mustEqual PriceValue(1.0)

      12.345 unit BTC mustEqual (12345 unit MBTC)
      23.45 unit CNY mustEqual (2345 unit CNY2)
      0 unit BTC mustEqual (0 unit MBTC)

      1 unit BTC to MBTC mustEqual (1000 unit MBTC)
      val goods = 1 unit BTC
      val money = 4000 unit CNY
      money / goods mustEqual (PriceValue(4000, (CNY, BTC)))

      // price conversions
      PriceValue(4000, (CNY, BTC)) to (CNY, MBTC) mustEqual (PriceValue(4.0, (CNY, MBTC)))
      PriceValue(4000, (CNY, BTC)) to (CNY2, MBTC) mustEqual (PriceValue(400, (CNY2, MBTC)))
      PriceValue(4000, (CNY, BTC)) to (BTC, CNY) mustEqual (PriceValue(1.0 / 4000, (BTC, CNY)))

      // multiply
      val amount = 2 unit BTC
      var price: PriceValue = 4000.0 unit (CNY, BTC)

      amount * price mustEqual (8000.0 unit CNY)
      price * amount mustEqual (8000.0 unit CNY)

      price = 4.0 unit (CNY, MBTC)
      amount * price mustEqual (8000.0 unit CNY)
      price * amount mustEqual (8000.0 unit CNY)
    }
  }
}