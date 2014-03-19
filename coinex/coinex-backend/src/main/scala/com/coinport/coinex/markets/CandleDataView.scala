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

class MarketCandleDataView extends ExtendedView {
  override val processorId = "coinex_mup"
  override val viewId = "candel_data_view"
  private val manager = new CandleDataManager

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(mu: OrderSubmitted, _) if mu.txs.nonEmpty =>
    case q: QueryMarketCandleData =>
      log.info("aloha!!!aloha!!!aloha!!!aloha!!!aloha!!!aloha!!!")
      val candleData = CandleData(timestamp = 0, items =
        Seq(CandleDataItem(1390914000,6579.6768,4820,4830,4790.54,4895.03),
        CandleDataItem(1390915000,6579.6768,4830,4840,4790.54,4895.03),
        CandleDataItem(1390916000,6579.6768,4840,4850,4790.54,4895.03),
        CandleDataItem(1390917000,6579.6768,4850,4860,4790.54,4895.03)))
      sender ! QueryMarketCandleDataResult(candleData)
  }
}

class CandleDataManager extends StateManager[CandleDataState] {
  initWithDefaultState(CandleDataState())

  val minute = 60 * 1000
  val quarter = 15 * minute
  val hour = 4 * quarter
  val day = 24 * hour
}