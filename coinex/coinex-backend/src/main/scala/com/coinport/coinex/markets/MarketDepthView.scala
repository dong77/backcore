/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
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

    case QueryMarket(side, maxDepth) if side == market =>
      val (asks, bids) = manager().get(maxDepth)
      sender ! QueryMarketResult(MarketDepth(market, asks, bids))
  }
}

// TODO(d): test this class.
class MarketDepthManager(market: MarketSide) extends StateManager[MarketDepthState] {
  initWithDefaultState(MarketDepthState())

  def adjustAmount(side: MarketSide, order: Order, addOrRemove: Boolean /*true for increase, false for reduce*/ ) {
    def adjust(amount: Long) = if (addOrRemove) amount else -amount
    if (side == market && order.price.isDefined) {
      state = state.adjustAsk(order.price.get, adjust(order.quantity))
    } else if (side == market.reverse && order.price.isDefined) {
      state = state.adjustBid(order.price.get, adjust(order.minimalTake))
    }
  }

  def reductAmount(side: MarketSide, tx: Transaction) = {
    val Transaction(_, taker, maker) = tx
    val (ask, bid) = if (side == market) (taker, maker) else (maker, taker)

    if (ask.previous.price.isDefined) {
      state = state.adjustAsk(ask.previous.price.get, -ask.outAmount)
    }
    if (bid.previous.price.isDefined && bid.previous.takeLimit.isDefined) {
      val v = if (bid.current.quantity == 0) -bid.previous.takeLimit.get else -bid.minimalTakeDiff
      state = state.adjustBid(bid.previous.price.get, v)
    }
  }
}