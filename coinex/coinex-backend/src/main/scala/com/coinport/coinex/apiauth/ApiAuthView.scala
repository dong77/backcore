/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.apiauth

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView

class ApiAuthView extends ExtendedView {
  override val processorId = "coinex_aap"
  override val viewId = "api_auth_view"

  def receive = LoggingReceive {
    case _ =>
  }
}