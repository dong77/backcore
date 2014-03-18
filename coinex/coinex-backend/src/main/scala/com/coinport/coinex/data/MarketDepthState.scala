/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import scala.collection.immutable.SortedMap
import MarketState._
import Implicits._

object MarketDepthState {
  type ItemMap = SortedMap[Double, Long]
  val EmptyItemMap = SortedMap.empty[Double, Long]
}

case class MarketDepthState(
    askMap: MarketDepthState.ItemMap = MarketDepthState.EmptyItemMap,
    bidMap: MarketDepthState.ItemMap = MarketDepthState.EmptyItemMap) {

  def adjustAsk(price: Double, amount: Long): MarketDepthState = {
    var old: Long = askMap.getOrElse(price, 0)
    val updatedAmount = old + amount
    var map = askMap - price
    if (updatedAmount <= 0) copy(askMap = map) else copy(askMap = map + (price -> updatedAmount))
  }

  def adjustBid(price: Double, amount: Long): MarketDepthState = {
    var old: Long = bidMap.getOrElse(price, 0)
    val updatedAmount = old + amount
    var map = bidMap - price
    if (updatedAmount <= 0) copy(bidMap = map) else copy(bidMap = map + (price -> updatedAmount))
  }

  def get(maxDepth: Int): (Seq[MarketDepthItem], Seq[MarketDepthItem]) = {
    val asks = askMap.take(maxDepth).toSeq.map(i => MarketDepthItem(i._1, i._2))
    val bids = bidMap.take(maxDepth).toSeq.map(i => MarketDepthItem(1 / i._1, i._2))
    (asks, bids)
  }
}