/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import com.coinport.coinex.common.ExtendedProcessor

// This is an empty processor to support various views.
class MarketUpdateProcessor extends ExtendedProcessor {
  override def processorId = "coinex_mup"

  def receiveMessage: Receive = {
    case _ =>
  }
}

