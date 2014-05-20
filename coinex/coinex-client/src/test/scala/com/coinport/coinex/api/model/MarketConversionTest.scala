/**
 * Copyright (C) 2014 Coinport Inc.
 */
package com.coinport.coinex.api.model

import org.specs2.mutable._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data._

class MarketConversionTest extends Specification {
  "market conversions" should {
    "market depth conversion" in {
      val bids = List(
        MarketDepthItem(450, 1500),
        MarketDepthItem(300, 2000),
        MarketDepthItem(200, 3000)
      )
      val asks = List(
        MarketDepthItem(550, 4500),
        MarketDepthItem(600, 5000),
        MarketDepthItem(700, 6000)
      )

      val backendObj = MarketDepth(Btc ~> Cny, asks = asks, bids = bids)
      val marketDepth = fromMarketDepth(backendObj)

      marketDepth.asks mustEqual List(ApiMarketDepthItem(5500.0, 4.5), ApiMarketDepthItem(6000.0, 5.0), ApiMarketDepthItem(7000.0, 6.0))
      marketDepth.bids mustEqual List(ApiMarketDepthItem(4500.0, 1.5), ApiMarketDepthItem(3000.0, 2.0), ApiMarketDepthItem(2000.0, 3.0))
    }

    "market conversion" in {
      Market(Btc, Ltc) mustEqual Market(Ltc, Btc)

      Market(Btc, Usd).toString mustEqual "BTCUSD"
      Market(Usd, Btc).toString mustEqual "BTCUSD"

      var market: Market = "LTCBTC"
      market mustEqual Market(Ltc, Btc)
      market = "XXCXXX"
      market mustEqual Market(Unknown, Unknown)

      Market(Btc, Usd).getMarketSide() mustEqual Btc ~> Usd
      Market(Btc, Usd).getMarketSide(false) mustEqual Usd ~> Btc
    }

    "market side conversion" in {
      Btc ~> Usd mustEqual MarketSide(Btc, Usd)
      Btc ~> Usd mustEqual Btc ~> Usd
      Btc ~> Usd mustNotEqual Usd ~> Btc
      (Btc ~> Usd) == (Btc ~> Usd) mustEqual true
      (Btc ~> Usd).reverse mustEqual (Usd ~> Btc)
    }
  }
}