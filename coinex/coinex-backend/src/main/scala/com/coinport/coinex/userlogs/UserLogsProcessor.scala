/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.userlogs

import com.coinport.coinex.common.ExtendedProcessor

// This is an empty processor to support its views.
class UserLogsProcessor extends ExtendedProcessor {
  override def processorId = "coinex_ulp"

  def receiveMessage: Receive = {
    case _ =>
  }
}

