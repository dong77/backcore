/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

import com.coinport.coinex.data._

case class ApiResult(success: Boolean = true, code: Int = 0, message: String = "", data: Option[Any] = None)

case class CurrencyObject(currency: String, value_int: Long, value: Double, display: String, display_short: String)

object CurrencyObject {
  def apply(currency: Currency, value_int: Long): CurrencyObject = {
    val externalValue = value_int.externalValue(currency)
    CurrencyObject(currency, value_int, externalValue, format(externalValue, currency), formatShort(externalValue))
  }

  def format(value: Double, currency: Currency): String = {
    val exponent = CurrencyConversion.getExponent(currency)
    val patten = "%." + exponent + "f"
    patten.format(value)
  }

  def formatShort(value: Double): String = "%.2f".format(value)
}

case class PriceObject(item: String, currency: String, value_int: Double, value: Double, display: String, display_short: String)

object PriceObject {
  def apply(side: MarketSide, value_int: Double): PriceObject = {
    val externalValue: Double = value_int.externalValue(side)
    val currency = side._2
    PriceObject(side._1, currency, value_int, externalValue, format(externalValue, currency), formatShort(externalValue))
  }

  def format(value: Double, currency: Currency): String = {
    val exponent = CurrencyConversion.getExponent(currency)
    val patten = "%." + exponent + "f"
    patten.format(value)
  }

  def formatShort(value: Double): String = "%.2f".format(value)
}

case class ApiSubmitOrderResult(order: UserOrder)

case class ApiAccountItem(currency: String, available: CurrencyObject, locked: CurrencyObject, pendingWithdrawal: CurrencyObject)

case class ApiUserAccount(uid: String, accounts: Map[String, ApiAccountItem] = Map())

case class ApiMarketDepthItem(price: Double, amount: Double)

case class ApiMarketDepth(bids: Seq[ApiMarketDepthItem], asks: Seq[ApiMarketDepthItem])

case class ApiTicker(market: String, price: PriceObject, high: PriceObject, low: PriceObject, volume: CurrencyObject, gain: Option[Double] = None, trend: Option[String] = None)

case class ApiTransaction(id: String, timestamp: Long, price: Double, subjectAmount: Double, currencyAmount: Double, maker: String, taker: String, sell: Boolean, tOrder: ApiOrderState, mOrder: ApiOrderState)

case class ApiOrderState(oid: String, uid: String, preAmount: Double, curAmount: Double)

case class ApiAssetItem(uid: String, assetMap: Map[String, Double], amountMap: Map[String, Double], timestamp: Long)

case class ApiTransferItem(id: String, uid: String, amount: CurrencyObject, status: Int, created: Long, updated: Long, operation: Int)