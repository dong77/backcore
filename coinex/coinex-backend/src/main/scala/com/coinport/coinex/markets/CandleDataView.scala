/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import com.coinport.coinex.data.ChartTimeDimension._
import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import scala.collection.mutable.Map
import Implicits._

class CandleDataView(market: MarketSide) extends ExtendedView {
  override def processorId = MARKET_UPDATE_PROCESSOR <<
  override val viewId = CANDLE_DATA_VIEW << market

  private val manager = new CandleDataManager(market)

  def receive = LoggingReceive {
    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      if (!txs.isEmpty) manager.updateCandleItem(txs.last)

    case QueryCandleData(side, dimension, from, to) if side == market || side == market.reverse =>
      sender ! QueryCandleDataResult(CandleData(manager.getCandleItems(dimension, from, to), side))
  }
}

class CandleDataManager(marketSide: MarketSide) extends Manager[TCandleDataState] {
  val minute = 60 * 1000
  val hour = 60 * 60 * 1000
  val day = 24 * 60 * 60 * 1000
  val week = 7 * 24 * 60 * 60 * 1000

  var candleMap = Map.empty[ChartTimeDimension, Map[Long, CandleDataItem]]
  ChartTimeDimension.list.foreach(d => candleMap.put(d, Map.empty[Long, CandleDataItem]))

  override def getSnapshot = TCandleDataState(candleMap)

  override def loadSnapshot(snapshot: TCandleDataState) {
    candleMap = candleMap.empty ++ snapshot.candleMap.map {
      x =>
        x._1 -> (Map.empty[Long, CandleDataItem] ++ x._2)
    }
  }

  def updateCandleItem(t: Transaction) {
    val tout = t.takerUpdate.previous.quantity - t.takerUpdate.current.quantity
    val tin = t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity
    val mprice = t.makerUpdate.current.price.get
    val timestamp = t.timestamp
    val (price, out, in) = if (t.side == marketSide) (1 / mprice, tout, tin) else (mprice, tin, tout)

    ChartTimeDimension.list.foreach {
      d =>
        val key = timestamp / getTimeSkip(d)
        val itemMap = candleMap.get(d).get
        val item = itemMap.get(key) match {
          case Some(item) =>
            CandleDataItem(key, item.inAoumt + in, item.outAoumt + out,
              item.open, item.close, Math.min(item.low, mprice), Math.max(item.high, mprice))
          case None =>
            CandleDataItem(key, in, out, price, price, price, price)
        }
        itemMap.put(key, item)
    }
  }

  def getCandleItems(dimension: ChartTimeDimension, from: Long, to: Long) = {
    val timeSkiper = getTimeSkip(dimension)
    val itemMap = candleMap.get(dimension).get
    (from / timeSkiper to to / timeSkiper).map(itemMap.get).filter(_.isDefined).map(_.get)
  }

  private def getTimeSkip(dimension: ChartTimeDimension) = dimension match {
    case OneMinute => minute
    case ThreeMinutes => 3 * minute
    case FiveMinutes => 5 * minute
    case FifteenMinutes => 15 * minute
    case ThirtyMinutes => 30 * minute
    case OneHour => hour
    case TwoHours => 2 * hour
    case FourHours => 4 * hour
    case SixHours => 6 * hour
    case TwelveHours => 12 * hour
    case OneDay => day
    case ThreeDays => 3 * day
    case OneWeek => week
  }
}
