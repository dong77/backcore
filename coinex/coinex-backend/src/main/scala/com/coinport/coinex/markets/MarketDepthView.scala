/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import Implicits._

class MarketDepthView(market: MarketSide) extends ExtendedView {
  override val processorId = "coinex_mup"
  override val viewId = "market_depth_view"
  val manager = new MarketDepthManager(market)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderCancelled(side, order), _) if side == market || side == market.reverse =>
      manager.adjustAmount(side, order, false)

    case e @ Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      manager.adjustAmount(orderInfo.side, orderInfo.order, true)
      txs foreach { manager.reductAmount(orderInfo.side, _) }

    case QueryMarketDepth(side, maxDepth) if side == market =>
      val (asks, bids) = manager().get(maxDepth)
      sender ! QueryMarketDepthResult(MarketDepth(market, asks, bids))
  }
}

class MarketDepthManager(market: MarketSide) extends Manager[MarketDepthState](MarketDepthState()) {

  def adjustAmount(side: MarketSide, order: Order, addOrRemove: Boolean /*true for increase, false for reduce*/ ) {
    def adjust(amount: Long) = if (addOrRemove) amount else -amount
    if (side == market && order.price.isDefined) {
      // ask
      state = state.adjustAsk(order.price.get, adjust(order.maxOutAmount(order.price.get)))
    } else if (side == market.reverse && order.price.isDefined) {
      // bid
      state = state.adjustBid(order.price.get, adjust(order.maxInAmount(order.price.get)))
    }
  }

  def reductAmount(side: MarketSide, tx: Transaction) = {
    val Transaction(_, _, taker, maker) = tx
    val (ask, bid) = if (side == market) (taker, maker) else (maker, taker)

    if (ask.previous.price.isDefined) {
      val diff = ask.current.maxOutAmount(ask.current.price.get) - ask.previous.maxOutAmount(ask.previous.price.get)
      state = state.adjustAsk(ask.previous.price.get, diff)
    }

    if (bid.previous.price.isDefined) {
      val diff = bid.current.maxInAmount(bid.current.price.get) - bid.previous.maxInAmount(bid.previous.price.get)
      state = state.adjustBid(bid.previous.price.get, diff)
    }
  }
}
