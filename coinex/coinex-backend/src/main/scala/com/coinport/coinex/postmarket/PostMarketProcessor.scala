/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.postmarket

import com.coinport.coinex.common.ExtendedProcessor

// This is an empty processor to support its views.
class PostMarketProcessor extends ExtendedProcessor {
  override def processorId = "coinex_pmp"

  def receiveMessage: Receive = {
    case _ =>
  }
}

