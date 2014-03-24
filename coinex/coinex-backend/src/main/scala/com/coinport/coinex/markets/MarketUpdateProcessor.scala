/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * TODO(c): change the name as AggregateProcessor and move to the other dir
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
    case p: ConfirmablePersistent => p.confirm()
    case _ =>
  }
}

