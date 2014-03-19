/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedProcessor
import akka.persistence._
import com.coinport.coinex.data._

// This is an empty processor to support various views.
class MarketUpdateProcessor extends ExtendedProcessor {
  override def processorId = "coinex_mup"

  def receive = LoggingReceive {
    case p @ ConfirmablePersistent(OrderSubmitted(originOrderInfo, txs), seq, _) =>
      p.confirm()
      log.debug("=======::::::::: " + p)
  }
}

