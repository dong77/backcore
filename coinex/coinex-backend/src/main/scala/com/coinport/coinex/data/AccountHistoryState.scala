/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */
package com.coinport.coinex.data

import scala.collection.SortedMap

object AccountHistoryState {
  //first key is timestamp, second key is UserId
  type userAssetMap = SortedMap[Long, Map[Long, UserAccount]]
  val emptyAssetMap = SortedMap.empty[Long, Map[Long, UserAccount]]
  //first key is timestamp
  type currencyMap = SortedMap[Long, Map[MarketSide, Double]]
  val emptyCurrencyMap = SortedMap.empty[Long, Map[MarketSide, Double]]
}

case class AccountHistoryState(assetMap: AccountHistoryState.userAssetMap = AccountHistoryState.emptyAssetMap,
    currencyMap: AccountHistoryState.currencyMap = AccountHistoryState.emptyCurrencyMap) {
  val day = 1000 * 60 * 60 * 24

  def addAsset(timestamp: Long, assetUpdate: Map[Long, UserAccount]) = {
    val timeDay = timestamp / day

    val deAssetMap = assetMap - timeDay
    val newAssetMap = assetMap.get(timeDay) match {
      case Some(asset) => timeDay -> (asset ++ assetUpdate)
      case None => timeDay -> assetUpdate
    }

    copy(assetMap = (deAssetMap + newAssetMap))
  }

  def addPrice(timestamp: Long, currencyUpdate: Map[MarketSide, Double]) = {
    val timeDay = timestamp / day

    val deCurrencyMap = currencyMap - timeDay
    val newCurrencyMap = currencyMap.get(timeDay) match {
      case Some(currency) => timeDay -> (currency ++ currencyUpdate)
      case None => timeDay -> currencyUpdate
    }

    copy(currencyMap = (deCurrencyMap + newCurrencyMap))
  }

  def getAsset(userId: Long, from: Long, to: Long): Map[Long, UserAccount] = {
    (from / day to to / day).map { i =>
      assetMap.get(i) match {
        case Some(userAsset) => (i -> userAsset.getOrElse(userId, null))
        case None => (i -> null)
      }
    }
  }.filter(_._2 != null).toMap

  def getPrice(userId: Long, from: Long, to: Long): Map[Long, Map[MarketSide, Double]] = {
    (from / day to to / day).map { i => i -> currencyMap.getOrElse(i, null) }
  }.filter(_._2 != null).toMap
}
