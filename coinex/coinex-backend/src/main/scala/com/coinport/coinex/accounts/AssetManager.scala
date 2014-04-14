package com.coinport.coinex.accounts

import com.coinport.coinex.common.Manager
import scala.collection.mutable.Map
import com.coinport.coinex.data._

class AssetManager extends Manager[TAssetState] {
  private val currencyMap = Map[Currency, Long]()
  private val timeAsset = Map.empty[Long, Map[Currency, Long]]
  private var assetMap = Map.empty[Long, Map[Long, Map[Currency, Long]]]

  private val timePrice = Map.empty[Long, Double]
  private var priceMap = Map.empty[MarketSide, Map[Long, Double]]

  val day = 1000 * 60 * 60 * 24

  // Thrift conversions     ----------------------------------------------
  def getSnapshot = TAssetState(assetMap, priceMap)

  def loadSnapshot(snapshot: TAssetState) = {
    assetMap = assetMap.empty ++ snapshot.userAssetMap.map(
      x => x._1 -> (timeAsset.empty ++ x._2.map(
        y => y._1 -> (currencyMap.empty ++ y._2))))

    priceMap = priceMap.take(0) ++ snapshot.marketPriceMap.map(
      x => x._1 -> (timePrice.empty ++ x._2))
  }

  def updateAsset(user: Long, timestamp: Long, currency: Currency, volume: Long) = {
    val timeDay = timestamp / day
    assetMap.get(user) match {
      case Some(t) => t.get(timeDay) match {
        case Some(ua) => ua.put(currency, ua.getOrElse(currency, 0L) + volume)
        case None => t.put(timeDay, Map(currency -> volume))
      }
      case None => assetMap.put(user, Map(timeDay -> Map(currency -> volume)))
    }
  }

  def updatePrice(side: MarketSide, timestamp: Long, price: Double) = {
    val timeDay = timestamp / day
    priceMap.get(side) match {
      case Some(pm) => pm.put(timeDay, price)
      case None => priceMap.put(side, Map(timeDay -> price))
    }
  }

  def getAsset(userId: Long, from: Long, to: Long) =
    assetMap.get(userId) match {
      case Some(timeAsset) =>
        (from / day to to / day).map(i => timeAsset.get(i).map(i -> _)).filter(_.isDefined).map(_.get).toMap
      case None => Map[Long, Map[Currency, Long]]().toMap
    }

  def getPrice(from: Long, to: Long) = {
    priceMap.map {
      case (side, timePrice) =>
        side -> (from / day to to / day).map(i => timePrice.get(i).map(i -> _)).filter(_.isDefined).map(_.get).toMap
    }
  }
}
