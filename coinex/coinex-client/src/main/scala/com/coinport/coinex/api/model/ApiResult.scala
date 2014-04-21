/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

case class ApiResult(success: Boolean = true, code: Int = 0, message: String = "", data: Option[Any] = None)

case class CurrencyObject(currency: String, display: String, display_short: String, value: Double, value_int: Double)

case class SubmitOrderResult(order: UserOrder)

case class UserAccount(uid: String, accounts: Map[String, Double] = Map())

case class MarketDepthItem(price: Double, amount: Double)

case class MarketDepth(bids: Seq[MarketDepthItem], asks: Seq[MarketDepthItem])

case class Ticker(price: CurrencyObject, high: CurrencyObject, low: CurrencyObject, volume: CurrencyObject, gain: Option[Double] = None, trend: Option[String] = None)

case class Transaction(id: String, timestamp: Long, price: Double, amount: Double, total: Double, maker: String, taker: String, sell: Boolean)

case class ApiTransferItem(id: String, uid: String, amount: CurrencyObject, status: Int, created: Long, updated: Long, operation: Int)

case class AssetItem(uid: String, assetMap: Map[String, Double], amountMap: Map[String, Double], timestamp: Long)
