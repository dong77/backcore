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

class MarketUpdateUserLogsView extends ExtendedView {
  override def processorId = "coinex_mup"
  private val manager = new MarketUpdateUserLogsManager

  def receive = {
    case DebugDump =>
      log.info("state: {}", manager())

    case x =>
      log.info("~~~ saw: " + x)
      if (receiveMessage.isDefinedAt(x)) receiveMessage(x)
  }

  def receiveMessage: Receive = {
    case Persistent(OrderCancelled(_, order), _) => manager.cancelOrder(order)

    case Persistent(mu: MarketUpdate, _) =>
      manager.addOrUpdateOrderInfo(mu.originOrderInfo)
      mu.matchedOrders foreach manager.addOrUpdateOrderInfo

    case q: QueryUserOrders =>
      sender ! QueryUserOrdersResult(q.userId, manager.getOrderInfos(q))
  }
}