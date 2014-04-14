/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object MarketService extends AkkaService {
  def getDepth(marketSide: MarketSide, depth: Int): Future[ApiResult] = {
    backend ? QueryMarketDepth(marketSide, depth) map {
      case result: QueryMarketDepthResult =>
        val depth: com.coinport.coinex.api.model.MarketDepth = result.marketDepth
        ApiResult(data = Some(depth))
      case x => ApiResult(false)
    }
  }

  def getHistory(marketSide: MarketSide, timeDimension: ChartTimeDimension, from: Long, to: Long): Future[ApiResult] = {
    backend ? QueryCandleData(marketSide, timeDimension, from, to) map {
      case rv: QueryCandleDataResult =>
        val candles = rv.candleData
        val map = candles.items.map(i => i.timestamp -> i).toMap
        val timeSkip: Long = timeDimension
        var open = 0.0
        val data = (from / timeSkip to to / timeSkip).map {
          key: Long =>
            map.get(key) match {
              case Some(item) =>
                open = item.close
                CandleDataItem(key * timeSkip, item.inAoumt, item.outAoumt, item.open, item.close, item.low, item.high)
              case None =>
                CandleDataItem(key * timeSkip, 0, 0, open, open, open, open)
            }
        }.toSeq
        ApiResult(data = Some(data))
      case x =>
        ApiResult(false)
    }
  }

  def getTransactions(marketSide: MarketSide, tid: Option[Long], uid: Option[Long], orderId: Option[Long], skip: Int, limit: Int): Future[ApiResult] = {
    val cursor = Cursor(skip, limit)
    // struct QueryTransaction {1: optional i64 tid, 2: optional i64 uid, 3: optional i64 oid, 4:optional MarketSide side, 5: Cursor cursor, 6: bool getCount}
    backend ? QueryTransaction(tid, uid, orderId, Some(QueryMarketSide(marketSide, true)), cursor, false) map {
      case result: QueryTransactionResult =>
        println("transaction: " + marketSide + " -> " + result)
        val subject = marketSide._1
        val currency = marketSide._2
        val items = result.transactionItems map {
          item: TransactionItem =>
            val takerSide = item.side
            val id = item.tid
            val timestamp = item.timestamp
            val isSell = marketSide == takerSide
            val price = (if (isSell) item.price.reverse else item.price).externalValue(marketSide)
            val volume = (if (isSell) item.amount else item.volume).externalValue(subject)
            val total = (if (isSell) item.volume else item.amount).externalValue(currency)
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
        ApiResult(data = Some(items))
    }
  }

  def getGlobalTransactions(marketSide: MarketSide, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, None, None, skip, limit)

  def getTransactionsByUser(marketSide: MarketSide, uid: Long, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, Some(uid), None, skip, limit)

  def getTransactionsByOrder(marketSide: MarketSide, orderId: Long, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, None, Some(orderId), skip, limit)

  def getTickers(marketSides: Set[MarketSide]) = {
    backend ? QueryMetrics map {
      case result: Metrics =>
        val data = result.metricsByMarket
          .filter(kv => marketSides.exists(_ == kv._1))
          .map {
            case (side, metrics) =>
              val side = metrics.side
              val currency: String = side._2
              val subject = side._1
              val internalPrice = metrics.price
              val externalPrice = internalPrice.externalValue(side)
              val internalHigh = metrics.high.getOrElse(0.0)
              val externalHigh = internalHigh.externalValue(side)
              val internalLow = metrics.low.getOrElse(0.0)
              val externalLow = internalLow.externalValue(side)
              val internalVolume = metrics.volume
              val externalVolume = internalVolume.externalValue(subject)
              val gain = metrics.gain
              val trend = Some(metrics.direction.toString.toLowerCase)

              com.coinport.coinex.api.model.Ticker(
                price = CurrencyObject(currency, externalPrice.toString, externalPrice.toString, externalPrice, internalPrice),
                volume = CurrencyObject(subject, externalVolume.toString, externalVolume.toString, externalVolume, internalVolume),
                high = CurrencyObject(currency, externalHigh.toString, externalHigh.toString, externalHigh, internalHigh),
                low = CurrencyObject(currency, externalLow.toString, externalLow.toString, externalLow, internalLow),
                gain = gain,
                trend = trend
              )
          }.toSeq
        ApiResult(data = Some(data))
    }
  }
}
