/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.postmarket

import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
import Implicits._

class CandleDataView extends ExtendedView {
  override def processorId = "coinex_pmp"
  private val manager = new CandleDataManager

  def receive = {
    case DebugDump =>
      log.info("state: {}", manager())

    case x =>
      log.info("~~~ saw: " + x)
      if (receiveMessage.isDefinedAt(x)) receiveMessage(x)
  }

  def receiveMessage: Receive = {
    case Persistent(mu: MarketUpdate, _) if mu.txs.nonEmpty =>
    //  val timestamp = mu.originOrderInfo.order.timestamp.get
    case q: QueryMarketCandleData =>
  }
}