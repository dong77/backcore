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
        val depth: com.coinport.coinex.api.model.ApiMarketDepth = result.marketDepth
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
    backend ? QueryTransaction(tid, uid, orderId, Some(QueryMarketSide(marketSide, true)), cursor, false) map {
      case result: QueryTransactionResult =>
        val subject = marketSide._1
        val currency = marketSide._2
        val items = result.transactions map { t =>
          val takerSide = t.side
          val isSell = marketSide == takerSide

          val takerAmount = t.takerUpdate.previous.quantity - t.takerUpdate.current.quantity
          val makerAmount = t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity
          val (sAmount, cAmount) = if (isSell) (makerAmount, takerAmount) else (takerAmount, makerAmount)

          val takerOrder = ApiOrderState(t.takerUpdate.current.id.toString, t.takerUpdate.current.userId.toString, t.takerUpdate.previous.quantity, t.takerUpdate.current.quantity)
          val makerOrder = ApiOrderState(t.makerUpdate.current.id.toString, t.makerUpdate.current.userId.toString, t.makerUpdate.previous.quantity, t.makerUpdate.current.quantity)

          ApiTransaction(
            id = t.id.toString,
            timestamp = t.timestamp,
            price = (cAmount.toDouble / sAmount.toDouble).externalValue(marketSide),
            subjectAmount = sAmount.externalValue(subject),
            currencyAmount = cAmount.externalValue(currency),
            taker = takerOrder.uid,
            maker = makerOrder.uid,
            sell = isSell,
            tOrder = takerOrder,
            mOrder = makerOrder
          )
        }
        ApiResult(data = Some(items))
    }
  }

  def getGlobalTransactions(marketSide: MarketSide, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, None, None, skip, limit)

  def getTransactionsByUser(marketSide: MarketSide, uid: Long, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, Some(uid), None, skip, limit)

  def getTransactionsByOrder(marketSide: MarketSide, orderId: Long, skip: Int, limit: Int): Future[ApiResult] = getTransactions(marketSide, None, None, Some(orderId), skip, limit)

  def getAsset(userId: Long, from: Long, to: Long, baseCurrency: Currency) = {
    backend ? QueryAsset(userId, from, to) map {
      case result: QueryAssetResult =>
        val timeSkip: Long = ChartTimeDimension.OneMinute
        val start = Math.min(from / timeSkip, to / timeSkip)
        val stop = Math.max(from / timeSkip, to / timeSkip)

        val historyAsset = result.historyAsset
        val historyPrice = result.historyPrice

        val currentPrice = result.currentPrice.priceMap
        val currencyPriceMap = scala.collection.mutable.Map.empty[Currency, Map[Long, Double]]
        historyPrice.priceMap.foreach {
          case (side, map) =>
            if (side._2 == baseCurrency) {
              var curPrice = currentPrice.get(side).get

              val priceMap = (start to stop).reverse.map { timeSpot =>
                curPrice = map.get(timeSpot).getOrElse(curPrice)
                timeSpot -> curPrice.externalValue(side)
              }.toMap
              currencyPriceMap.put(side._1, priceMap)
            }
        }

        val currentAsset = scala.collection.mutable.Map.empty[Currency, Long] ++ result.currentAsset.currentAsset
        val assetList = (start to stop).reverse.map { timeSpot =>
          val rv = (timeSpot, currentAsset.clone())

          historyAsset.currencyMap.get(timeSpot) match {
            case Some(curMap) =>
              curMap.foreach {
                case (cur, volume) =>
                  currentAsset.put(cur, currentAsset.get(cur).get - volume)
              }
            case None =>
          }
          rv
        }

        val items = assetList.map {
          case (timeSpot, assetMap) =>
            val amountMap = assetMap.map {
              case (cur, volume) =>
                val amount =
                  if (cur == baseCurrency) volume * 1
                  else currencyPriceMap.get(cur) match {
                    case Some(curHisMap) => curHisMap.get(timeSpot).get * volume
                    case None => 0.0
                  }
                cur.toString.toUpperCase -> amount.externalValue(cur)
            }.toMap
            ApiAssetItem(uid = userId.toString,
              assetMap = assetMap.map(a => a._1.toString.toUpperCase -> a._2.externalValue(a._1)).toMap,
              amountMap = amountMap,
              timestamp = timeSpot * timeSkip)
        }
        ApiResult(data = Some(items.reverse))
    }
  }

  def getTickers(marketSides: Seq[MarketSide]) = {
    backend ? QueryMetrics map {
      case result: Metrics =>
        val map = result.metricsByMarket
        val data = marketSides
          .filter(s => map.contains(s))
          .map {
            side: MarketSide =>
              val subject = side._1
              val metrics = map.get(side).get
              val price = metrics.price
              val high = metrics.high.getOrElse(0.0)
              val low = metrics.low.getOrElse(0.0)
              val internalVolume = metrics.volume
              val gain = metrics.gain
              val trend = Some(metrics.direction.toString.toLowerCase)

              ApiTicker(
                market = side.S,
                price = PriceObject(side, price),
                volume = CurrencyObject(subject, internalVolume),
                high = PriceObject(side, high),
                low = PriceObject(side, low),
                gain = gain,
                trend = trend
              )
          }
        ApiResult(data = Some(data))
    }
  }
}
