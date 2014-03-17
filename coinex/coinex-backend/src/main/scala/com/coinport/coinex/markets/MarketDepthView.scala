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
import scala.collection.immutable.SortedMap

class MarketDepthView(side: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mup"

  val manager = new MarketDepthManager(side)
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
      manager.adjustAmount(side, orderInfo.order, true)
      txs foreach { manager.reductAmount(side, _) }
  }
}

object MarketDepthState {
  type ItemMap = SortedMap[Double, Long]
  val EmptyItemMap = SortedMap.empty[Double, Long]
}

case class MarketDepthState(
  askMap: MarketDepthState.ItemMap = MarketDepthState.EmptyItemMap,
  bidMap: MarketDepthState.ItemMap = MarketDepthState.EmptyItemMap)

// TODO(d): test this class.
class MarketDepthManager(market: MarketSide) extends StateManager[MarketDepthState] {
  initWithDefaultState(MarketDepthState())

  def adjustAmount(side: MarketSide, order: Order, addOrRemove: Boolean /*true for add, false for remove*/ ) {
    def adjust(amount: Long) = if (addOrRemove) amount else -amount
    if (side == market) {
      if (order.price.isDefined) {
        adjustAsk(order.price.get, adjust(order.quantity))
      }
    } else if (side == market.reverse) {
      if (order.price.isDefined && order.takeLimit.isDefined) {
        adjustAsk(order.price.get, adjust(order.takeLimit.get))
      }
    } else throw new IllegalArgumentException(side + " not supported by MarketDepthManager for " + market)
  }

  def reductAmount(side: MarketSide, tx: Transaction) = {
    val Transaction(_, taker, maker) = tx
    val (ask, bid) =
      if (side == market) (taker, maker)
      else if (side == market.reverse) (maker, taker)
      else throw new IllegalArgumentException(side + " not supported by MarketDepthManager for " + market)

    if (ask.previous.price.isDefined) {
      adjustAsk(ask.previous.price.get, -ask.outAmount)
    }
    if (bid.previous.price.isDefined && bid.previous.takeLimit.isDefined) {
      adjustBid(bid.previous.price.get, -bid.takeLimitDiff)
    }
  }

  private def adjustAsk(price: Double, amount: Long) = {
    var old: Long = state.askMap.getOrElse(price, 0)
    val updatedAmount = old + amount
    var map = state.askMap - price
    if (updatedAmount > 0) {
      state = state.copy(askMap = map + (price -> updatedAmount))
    } else if (updatedAmount < 0) {
      throw new IllegalArgumentException("Market depth askMap found minus value %d for price %d".format(updatedAmount, price))
    }
  }

  private def adjustBid(price: Double, amount: Long) = {
    val priceReverse = 1 / price
    var old: Long = state.bidMap.getOrElse(priceReverse, 0)
    val updatedAmount = old + amount
    var map = state.bidMap - priceReverse
    if (updatedAmount > 0) {
      state = state.copy(bidMap = map + (priceReverse -> updatedAmount))
    } else if (updatedAmount < 0) {
      throw new IllegalArgumentException("Market depth bidMap found minus value %d for price %d".format(updatedAmount, price))
    }
  }

}