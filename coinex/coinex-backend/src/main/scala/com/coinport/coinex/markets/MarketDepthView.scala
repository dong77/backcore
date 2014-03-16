/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import scala.collection.mutable
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
import Implicits._

class MarketDepthView(side: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mup"
  val manager = new MarketDepthManager(side)
  def receive = {
    case DebugDump =>
    // log.info("state: {}", manager())

    case x =>
      log.info("~~~ saw: " + x)
      if (receiveMessage.isDefinedAt(x)) receiveMessage(x)
  }

  def receiveMessage: Receive = {
    case Persistent(OrderCancelled(side, order), _) if side == side =>
      order.price foreach { manager.adjustAmount(side, _, -order.quantity) }

    case Persistent(OrderSubmitted(oi, txs), _) if oi.side == side =>
      oi.order.price foreach { manager.adjustAmount(side.reverse, _, oi.order.quantity - oi.outAmount) }
      txs.foreach { tx =>
        tx.makerUpdate.price foreach { manager.adjustAmount(side.reverse, _, -tx.makerUpdate.outAmount) }
      }
  }
}
object MarketDepthState {

}

// TODO
case class MarketDepthState()
// 2: list<MarketDepthItem> asks
//	3: list<MarketDepthItem> bids

class MarketDepthManager(side: MarketSide) extends StateManager[MarketDepthState] {
  initWithDefaultState(MarketDepthState())
  def adjustAmount(side: MarketSide, price: Double, amountAdjustment: Long) = {
  }
}