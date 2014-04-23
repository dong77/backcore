package com.coinport.coinex.serializers

import org.specs2.mutable._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.data.Currency._
import org.json4s.native.Serialization
import org.json4s.NoTypeHints
import scala.collection.immutable

case class CurrencyWrapper(currency: Currency)
case class MarketSideWrapper(side: MarketSide)

class PrettyJsonSerializerTest extends Specification {
  implicit val formats = Serialization.formats(NoTypeHints)
  "Any to JSON" should {
    "Currency to JSON" in {
      val json = ("currency" -> "BTC")
      PrettyJsonSerializer.toJson(CurrencyWrapper(Btc)) mustEqual writePretty(json)
    }

    "MarketSide to JSON" in {
      val json = ("side" -> "BTCUSD")
      PrettyJsonSerializer.toJson(MarketSideWrapper(Btc ~> Usd)) mustEqual writePretty(json)
    }

    "Map[MarketSide, Double] to JSON" in {
      val map = immutable.Map(
        Btc ~> Usd -> 456.7,
        Ltc ~> Usd -> 12.3
      )

      val json =
        ("BTCUSD" -> 456.7) ~
          ("LTCUSD" -> 12.3)

      PrettyJsonSerializer.toJson(map) mustEqual writePretty(json)
    }
  }

}
