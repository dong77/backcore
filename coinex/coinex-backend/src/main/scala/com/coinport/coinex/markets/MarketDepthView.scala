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
      if (!orderInfo.order.refundReason.isDefined)
        manager.adjustAmount(orderInfo.side, orderInfo.order, true)
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
  def adjustAsk(price: Double, amount: Long) = {
    val updatedAmount = askMap.getOrElse(price, 0L) + amount
    if (updatedAmount > 0) askMap += (price -> updatedAmount)
    else askMap -= price
  }

  def adjustBid(price: Double, amount: Long) = {
    val updatedAmount = bidMap.getOrElse(price, 0L) + amount
    if (updatedAmount > 0) bidMap += (price -> updatedAmount)
    else bidMap -= price
  }

  def get(maxDepth: Int): (Seq[MarketDepthItem], Seq[MarketDepthItem]) = {
    val asks = askMap.take(maxDepth).toSeq.map(i => MarketDepthItem(i._1, i._2))
    val bids = bidMap.take(maxDepth).toSeq.map(i => MarketDepthItem(1 / i._1, i._2))
    (asks, bids)
  }

  def adjustAmount(side: MarketSide, order: Order, addOrRemove: Boolean /*true for increase, false for reduce*/ ) {
    def adjust(amount: Long) = if (addOrRemove) amount else -amount
    if (side == market && order.price.isDefined) {
      adjustAsk(order.price.get, adjust(order.maxOutAmount(order.price.get)))
    } else if (side == market.reverse && order.price.isDefined) {
      adjustBid(order.price.get, adjust(order.maxInAmount(order.price.get)))
    }
  }

  def reduceAmount(side: MarketSide, tx: Transaction) = {
    val Transaction(_, _, _, taker, maker, _) = tx
    val (ask, bid) = if (side == market) (taker, maker) else (maker, taker)

    if (ask.previous.price.isDefined) {
      val diff = ask.current.maxOutAmount(ask.current.price.get) - ask.previous.maxOutAmount(ask.previous.price.get)
      adjustAsk(ask.previous.price.get, diff)
      if (ask.current.refundReason.isDefined)
        adjustAsk(ask.previous.price.get, -ask.current.maxOutAmount(ask.current.price.get))
    }

    if (bid.previous.price.isDefined) {
      val diff = bid.current.maxInAmount(bid.current.price.get) - bid.previous.maxInAmount(bid.previous.price.get)
      adjustBid(bid.previous.price.get, diff)
      if (bid.current.refundReason.isDefined)
        adjustBid(bid.previous.price.get, -bid.current.maxInAmount(bid.current.price.get))
    }
  }
}
