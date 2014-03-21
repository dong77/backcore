package com.coinport.coinex.data

import scala.collection.immutable.SortedMap
import com.coinport.coinex.data.ChartTimeDimension._

/**
 * Created by chenxi on 3/19/14.
 */

object CandleDataState {
  type ItemMap = Map[ChartTimeDimension, Map[Long, CandleDataItem]]
  val EmptyItemMap = ChartTimeDimension.list.map(t => t -> Map.empty[Long, CandleDataItem]).toMap
}

case class CandleDataState(candleMap: CandleDataState.ItemMap = CandleDataState.EmptyItemMap,
    reverseCandleMap: CandleDataState.ItemMap = CandleDataState.EmptyItemMap) {
  val minute = 60 * 1000
  val hour = 60 * 60 * 1000
  val day = 24 * 60 * 60 * 1000
  val week = 7 * 24 * 60 * 60 * 1000

  def addItem(dimension: ChartTimeDimension, timestamp: Long, price: Double, amount: Long): CandleDataState = {
    val updateMap = candleMap.get(dimension).get
    val removedTimeMap = candleMap - dimension
    copy(candleMap = removedTimeMap ++ Map(dimension -> updateCandleData(updateMap, timestamp / getTimeSkip(dimension), price, amount)))
  }

  def addReverseItem(dimension: ChartTimeDimension, timestamp: Long, price: Double, amount: Long): CandleDataState = {
    val updateMap = reverseCandleMap.get(dimension).get
    val removedTimeMap = reverseCandleMap - dimension
    copy(reverseCandleMap = removedTimeMap ++ Map(dimension -> updateCandleData(updateMap, timestamp / getTimeSkip(dimension), price, amount)))
  }

  def getItem(dimension: ChartTimeDimension, from: Long, to: Long): Seq[CandleDataItem] = {
    candleMap.get(dimension) match {
      case None => Nil
      case Some(map) =>
        val timeSkiper = getTimeSkip(dimension)
        findCandleData(from / timeSkiper, to / timeSkiper, map)
    }
  }

  def getReverseItem(dimension: ChartTimeDimension, from: Long, to: Long): Seq[CandleDataItem] = {
    reverseCandleMap.get(dimension) match {
      case None => Nil
      case Some(map) =>
        val timeSkiper = getTimeSkip(dimension)
        findCandleData(from / timeSkiper, to / timeSkiper, map)
    }
  }

  private def findCandleData(start: Long, stop: Long, map: Map[Long, CandleDataItem]): Seq[CandleDataItem] = {
    (start to stop).map { key =>
      map.get(key) match {
        case Some(item) =>
          CandleDataItem(key, item.volumn, item.open, item.close, item.low, item.high)
        case None => null
      }
    }
  }.filter(_ != null)

  private def updateCandleData(map: Map[Long, CandleDataItem], key: Long, price: Double, amount: Long) = {
    map.get(key) match {
      case None => map + (key -> CandleDataItem(key, amount, price, price, price, price))
      case Some(item) =>
        val newMap = map - key
        newMap + (key -> CandleDataItem(key, item.volumn + amount, item.open, price, Math.min(item.low, price), Math.max(item.high, price)))
    }
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
