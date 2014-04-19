/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.coinport.coinex.data.Implicits._

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
                CandleDataItem(key * timeSkip, item.inAoumt, item.outAoumt, item.open, item.close, item.low, item.high, item.side)
              case None =>
                CandleDataItem(key * timeSkip, 0, 0, open, open, open, open, marketSide)
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
              id = id.toString,
              timestamp = timestamp,
              price = price,
              amount = volume,
              total = total,
              taker = taker.toString,
              maker = maker.toString,
              sell = isSell
            )
        }
        ApiResult(data = Some(items))
    }
  }

  def getGlobalTransactions(marketSide: MarketSide, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, None, None, skip, limit)

  def getTransactionsByUser(marketSide: MarketSide, uid: Long, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, Some(uid), None, skip, limit)

  def getTransactionsByOrder(marketSide: MarketSide, orderId: Long, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, None, Some(orderId), skip, limit)

  def getAsset(userId: Long, from: Long, to: Long, baseCurrency: Currency) = {
    val timeSkip: Long = ChartTimeDimension.OneDay
    val start = Math.max(from / timeSkip, to / timeSkip)
    val stop = Math.min(from / timeSkip, to / timeSkip)

    backend ? QueryAsset(userId, from, to) map {
      case result: QueryAssetResult =>
        val historyAsset = result.historyAsset
        val historyPrice = result.historyPrice

        val currentPrice = result.currentPrice.priceMap
        val currencyPriceMap = historyPrice.priceMap.map {
          case (side, map) =>
            var curPrice = currentPrice.get(side).get

            val priceMap = (start to stop).map { timeSpot =>
              val tmpPrice = map.get(timeSpot).getOrElse(curPrice)
              curPrice = tmpPrice
              if (side._2 == baseCurrency) timeSpot -> tmpPrice else timeSpot -> tmpPrice.reverse
            }.toMap

            baseCurrency -> priceMap
        }

        var currentAsset = scala.collection.mutable.Map.empty[Currency, Long] ++ result.currentAsset.currentAsset
        val assetList = (Math.max(start, stop) to Math.min(start, stop)).map { timeSpot =>
          currentAsset = historyAsset.currencyMap.get(timeSpot) match {
            case Some(curMap) =>
              curMap.foreach {
                case (cur, volume) =>
                  currentAsset.put(cur, currentAsset.get(cur).get + volume)
              }
              currentAsset
            case None => currentAsset
          }
          (timeSpot, currentAsset.clone)
        }

        println("#######################")
        println("!!!!!assetList!!!!!!!!!" + assetList)
        println("#######################")
        println("!!!!!currentPriceMap!!!!!!!!!" + currencyPriceMap)
        println("#######################")

        val items = assetList.map {
          case (timeSpot, assetMap) =>
            val amount = assetMap.map {
              case (cur, volume) =>
                currencyPriceMap.get(cur).get.get(timeSpot).get * volume
            }.sum
            AssetItem(uid = userId.toString,
              assetMap = assetMap.map(a => a._1.toString -> a._2.toDouble).toMap,
              amount = amount,
              timestamp = timeSpot * timeSkip)
        }
        ApiResult(data = Some(items.reverse))
    }
  }

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
