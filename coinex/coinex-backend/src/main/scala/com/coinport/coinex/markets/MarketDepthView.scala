/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import Implicits._
import scala.collection.SortedMap

class MarketDepthView(market: MarketSide) extends ExtendedView {
  override val processorId = MARKET_UPDATE_PROCESSOR <<
  override val viewId = MARKET_DEPTH_VIEW << market
  val manager = new MarketDepthManager(market)

  def receive = LoggingReceive {
    case Persistent(OrderCancelled(side, order), _) if side == market || side == market.reverse =>
      manager.adjustAmount(side, order, false)

    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      val order = txs.lastOption.map(_.takerUpdate.current).getOrElse(orderInfo.order)
      manager.adjustAmount(orderInfo.side, order, true)
      txs foreach { manager.reduceAmount(orderInfo.side, _) }

    case QueryMarketDepth(side, maxDepth) if side == market =>
      val (asks, bids) = manager.get(maxDepth)
      sender ! QueryMarketDepthResult(MarketDepth(market, asks, bids))
  }
}

class MarketDepthManager(market: MarketSide) extends Manager[TMarketDepthState] {
  // Internal mutable state ----------------------------------------------
  var askMap = SortedMap.empty[Double, Long]
  var bidMap = SortedMap.empty[Double, Long]

  // Thrift conversions     ----------------------------------------------
  def getSnapshot = TMarketDepthState(askMap, bidMap)

  def loadSnapshot(snapshot: TMarketDepthState) = {
    askMap = askMap.take(0) ++ snapshot.askMap
    bidMap = bidMap.take(0) ++ snapshot.bidMap
  }

  // Business logics      ----------------------------------------------
  def get(maxDepth: Int): (Seq[MarketDepthItem], Seq[MarketDepthItem]) = {
    val asks = askMap.take(maxDepth).toSeq.map(i => MarketDepthItem(i._1, i._2))
    val bids = bidMap.take(maxDepth).toSeq.map(i => MarketDepthItem(1 / i._1, i._2))
    (asks, bids)
  }

  def adjustAmount(side: MarketSide, order: Order, addOrRemove: Boolean /*true for increase, false for reduce*/ ) =
    if (order.price.isDefined) {
      def adjust(amount: Long) = if (addOrRemove) amount else -amount
      val price = order.price.get
      if (side == market) adjustAsk(price, adjust(order.maxOutAmount(price)))
      else adjustBid(price, adjust(order.maxInAmount(price)))
    }

  def reduceAmount(side: MarketSide, tx: Transaction) = {
    val maker = tx.makerUpdate
    val price = maker.current.price.get
    if (side == market) adjustAsk(price, maker.current.maxOutAmount(price) - maker.previous.maxOutAmount(price))
    else adjustBid(price, maker.current.maxInAmount(price) - maker.previous.maxInAmount(price))
  }

  private def adjustAsk(price: Double, amount: Long) = {
    val updatedAmount = askMap.getOrElse(price, 0L) + amount
    if (updatedAmount > 0) askMap += (price -> updatedAmount)
    else askMap -= price
  }

  private def adjustBid(price: Double, amount: Long) = {
    val updatedAmount = bidMap.getOrElse(price, 0L) + amount
    if (updatedAmount > 0) bidMap += (price -> updatedAmount)
    else bidMap -= price
  }
}
