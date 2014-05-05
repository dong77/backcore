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
    Currency.valueOf(currencyString.toLowerCase.capitalize).getOrElse(Unknown)
  }

  implicit def currency2String(currency: Currency): String = {
    currency.name.toUpperCase
  }

  // user account conversions
  implicit def fromUserAccount(backendObj: com.coinport.coinex.data.UserAccount): com.coinport.coinex.api.model.ApiUserAccount = {
    val uid = backendObj.userId
    val map: Map[String, ApiAccountItem] = backendObj.cashAccounts.map {
      case (k: Currency, v: CashAccount) =>
        val currency: String = k
        currency -> ApiAccountItem(
          currency,
          CurrencyObject(k, v.available),
          CurrencyObject(k, v.locked),
          CurrencyObject(k, v.pendingWithdrawal)
        )
    }.toMap

    com.coinport.coinex.api.model.ApiUserAccount(uid.toString, accounts = map)
  }

  // market depth conversions
  implicit def fromMarketDepth(backendObj: com.coinport.coinex.data.MarketDepth): com.coinport.coinex.api.model.ApiMarketDepth = {
    val side = backendObj.side
    val subject = side._1
    val mapper = {
      item: com.coinport.coinex.data.MarketDepthItem =>
        com.coinport.coinex.api.model.ApiMarketDepthItem(item.price.externalValue(side), item.quantity.externalValue(subject))
    }
    val bids = backendObj.bids.map(mapper).toSeq
    val asks = backendObj.asks.map(mapper).toSeq
    com.coinport.coinex.api.model.ApiMarketDepth(bids = bids, asks = asks)
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

  class CandleDataItemSerializer() extends CustomSerializer[CandleDataItem](
    format => ({
      null // deserializer is not implemented
    }, {
      case candleDataItem: CandleDataItem =>
        val side = candleDataItem.side
        JArray(List(
          JDecimal(candleDataItem.timestamp),
          JDouble(candleDataItem.open.externalValue(side)),
          JDouble(candleDataItem.high.externalValue(side)),
          JDouble(candleDataItem.low.externalValue(side)),
          JDouble(candleDataItem.close.externalValue(side)),
          JDouble(candleDataItem.outAoumt.externalValue(side._1))
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

  implicit def fromOrderSubmitted(obj: OrderSubmitted): ApiSubmitOrderResult = {
    val orderInfo = obj.originOrderInfo
    val order = UserOrder.fromOrderInfo(orderInfo)
    ApiSubmitOrderResult(order)
  }
}
