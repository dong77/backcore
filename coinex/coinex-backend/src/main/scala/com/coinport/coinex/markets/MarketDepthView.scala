/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
import Implicits._

class MarketDepthView(market: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mup"

  val manager = new MarketDepthManager(market)
  def receive = {
    case DebugDump =>
      log.info("state: {}", manager())

    case x =>
      log.info("~~~ saw: " + x)
      if (receiveMessage.isDefinedAt(x)) receiveMessage(x)
      log.info("-" * 100 + "\n market depth state: {}", manager())
  }

  def receiveMessage: Receive = {
    case Persistent(OrderCancelled(side, order), _) =>
      manager.adjustAmount(side, order, false)

    case Persistent(OrderSubmitted(orderInfo, txs), _) =>
      manager.adjustAmount(market, orderInfo.order, true)
      txs foreach { manager.reductAmount(market, _) }

    case QueryMarket(side, maxDepth) =>
      if (side != market) {
        sender ! QueryMarketUnsupportedMarketFailure(side)
      } else {
        val (asks, bids) = manager().get(maxDepth)
        sender ! QueryMarketResult(MarketDepth(market, asks, bids))
      }
  }
}

// TODO(d): test this class.
class MarketDepthManager(market: MarketSide) extends StateManager[MarketDepthState] {
  initWithDefaultState(MarketDepthState())

  def adjustAmount(side: MarketSide, order: Order, addOrRemove: Boolean /*true for increase, false for reduce*/ ) {
    def adjust(amount: Long) = if (addOrRemove) amount else -amount
    if (side == market && order.price.isDefined) {
      state = state.adjustAsk(order.price.get, adjust(order.quantity))
    } else if (side == market.reverse && order.price.isDefined && order.takeLimit.isDefined) {
      state = state.adjustBid(order.price.get, adjust(order.takeLimit.get))
    }
  }

  def reductAmount(side: MarketSide, tx: Transaction) = {
    val Transaction(_, taker, maker) = tx
    val (ask, bid) =
      if (side == market) (taker, maker)
      else if (side == market.reverse) (maker, taker)
      else throw new IllegalArgumentException(side + " not supported by MarketDepthManager for " + market)

    if (ask.previous.price.isDefined) {
      state = state.adjustAsk(ask.previous.price.get, -ask.outAmount)
    }
    if (bid.previous.price.isDefined && bid.previous.takeLimit.isDefined) {
      state = state.adjustBid(bid.previous.price.get, -bid.takeLimitDiff)
    }
  }

}