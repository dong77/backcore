/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView

class MarketView(marketSide: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mp_" + marketSide
  val manager = new MarketManager(marketSide)

  def receive: Receive = {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(DoCancelOrder(side, orderId), _) =>
      manager.removeOrder(side, orderId)

    case Persistent(OrderSubmitted(side, order: Order), _) =>
      manager.addOrder(side, order)

    case QueryMarket(side, depth) =>
      sender ! QueryMarketResult(
        manager().limitPriceOrderPool(side).take(depth).toSeq,
        manager().limitPriceOrderPool(side.reverse).take(depth).toSeq)
  }
}
