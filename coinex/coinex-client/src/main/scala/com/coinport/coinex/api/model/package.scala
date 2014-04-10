/**
 * Copyright (C) 2014 Coinport Inc.
 */
package com.coinport.coinex.api

import com.coinport.coinex.data.ChartTimeDimension._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.data._
import org.json4s.ext.EnumNameSerializer
import org.json4s.JsonAST.JField
import org.json4s.native.Serialization
import org.json4s._
import scala.concurrent.duration._
import scala.Some

package object model {
  implicit def long2CurrencyWrapper(value: Long) = new CurrencyWrapper(value)

  implicit def double2CurrencyWrapper(value: Double) = new CurrencyWrapper(value)

  implicit def double2PriceWrapper(value: Double): PriceWrapper = new PriceWrapper(value)

  implicit def priceWrapper2Double(value: PriceWrapper) = value

  implicit def string2Currency(currencyString: String): Currency = {
    currencyString.toUpperCase match {
      case "RMB" => Currency.Rmb
      case "CNY" => Currency.Rmb
      case "BTC" => Currency.Btc
      case "LTC" => Currency.Ltc
      case "PTS" => Currency.Pts
      case "USD" => Currency.Usd
      case _ => null
    }
  }

  implicit def currency2String(currency: Currency): String = {
    currency match {
      case Currency.Rmb => "RMB"
      case Currency.Btc => "BTC"
      case Currency.Usd => "USD"
      case Currency.Ltc => "LTC"
      case Currency.Pts => "PTS"
      case _ => currency.name.toUpperCase
    }
  }

  // user account conversions
  implicit def fromUserAccount(backendObj: com.coinport.coinex.data.UserAccount): com.coinport.coinex.api.model.UserAccount = {
    val uid = backendObj.userId
    val map: Map[String, Double] = backendObj.cashAccounts.map {
      case (k: Currency, v: CashAccount) =>
        val currency: String = k
        currency -> v.available.externalValue(k)
    }.toMap

    com.coinport.coinex.api.model.UserAccount(uid, accounts = map)
  }

  // market depth conversions
  implicit def fromMarketDepth(backendObj: com.coinport.coinex.data.MarketDepth): com.coinport.coinex.api.model.MarketDepth = {
    val side = backendObj.side
    val subject = side._1
    val mapper = {
      item: com.coinport.coinex.data.MarketDepthItem =>
        com.coinport.coinex.api.model.MarketDepthItem(item.price.externalValue(side), item.quantity.externalValue(subject))
    }
    val bids = backendObj.bids.map(mapper)
    val asks = backendObj.asks.map(mapper)
    com.coinport.coinex.api.model.MarketDepth(bids = bids, asks = asks)
  }

  // candle data conversions
  implicit def timeDimension2MilliSeconds(dimension: ChartTimeDimension): Long = {
    val duration = dimension match {
      case OneMinute => 1 minute
      case ThreeMinutes => 3 minutes
      case FiveMinutes => 5 minutes
      case FifteenMinutes => 15 minutes
      case ThirtyMinutes => 30 minutes
      case OneHour => 1 hour
      case TwoHours => 2 hours
      case FourHours => 4 hours
      case SixHours => 6 hours
      case TwelveHours => 12 hours
      case OneDay => 1 day
      case ThreeDays => 3 days
      case OneWeek => 7 days
    }
    duration.toMillis
  }

  // ticker conversions
  implicit def metrics2Ticker(metrics: MetricsByMarket): com.coinport.coinex.api.model.Ticker = {
    val side = metrics.side
    val currency: String = side._1
    val subject = side._1
    val price = metrics.price.externalValue(side)
    val high = metrics.high.map(_.externalValue(side))
    val low = metrics.low.map(_.externalValue(side))
    val volume = metrics.volume.externalValue(subject)
    val gain = metrics.gain
    val trend = Some(metrics.direction.toString.toLowerCase)

    com.coinport.coinex.api.model.Ticker(
      currency = currency,
      price = price,
      volume = volume,
      high = high,
      low = low,
      gain = gain,
      trend = trend
    )
  }

  // transaction conversions
  implicit def fromTransactionItem(item: TransactionItem): com.coinport.coinex.api.model.Transaction = {
    val side = item.side
    val subject = side._1
    val currency = side._2
    val id = item.tid
    val timestamp = item.timestamp
    val price = item.price.externalValue(side)
    val volume = item.volume.externalValue(subject)
    val total = item.amount.externalValue(currency)
    // TODO: use Market+Operation model
    val isSell = side._2 equals Rmb
    val taker = item.taker
    val maker = item.maker

    com.coinport.coinex.api.model.Transaction(
      id = id,
      timestamp = timestamp,
      price = price,
      amount = volume,
      total = total,
      taker = taker,
      maker = maker,
      sell = isSell
    )
  }

  class CandleDataItemSerializer extends CustomSerializer[CandleDataItem](
    format => ({
      null // deserializer is not implemented
    }, {
      case candleDataItem: CandleDataItem =>
        val side = Btc ~> Rmb // TODO: put side in CandleDataItem
        JArray(List(
          JDecimal(candleDataItem.timestamp),
          JDouble(candleDataItem.open.externalValue(side)),
          JDouble(candleDataItem.high.externalValue(side)),
          JDouble(candleDataItem.low.externalValue(side)),
          JDouble(candleDataItem.close.externalValue(side)),
          JDouble(candleDataItem.volumn.externalValue(Btc)) // TODO: remove hardcoded subject
        ))
    })
  )

  implicit val formats = Serialization.formats(NoTypeHints) + new EnumNameSerializer(Operations) + new CandleDataItemSerializer()

  class JsonSupportWrapper(obj: Any) {
    def toJson(): JValue = {
      val json = Extraction.decompose(obj)
      json filterField {
        case JField(name, value) =>
          !name.startsWith("_") // filter fields starting with underscore
        case _ => false
      }
      json
    }
  }

  implicit def toJsonSupportWrapper(obj: Any): JsonSupportWrapper = new JsonSupportWrapper(obj)

  implicit def fromOrderSubmitted(obj: OrderSubmitted): SubmitOrderResult = {
    val orderInfo = obj.originOrderInfo
    val order = UserOrder.fromOrderInfo(orderInfo)
    SubmitOrderResult(order)
  }
}
