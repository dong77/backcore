/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.users

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView

class UserView extends ExtendedView {
  override val processorId = "coinex_up"
  override val viewId = "user_view"

  def receive = LoggingReceive {
    case _ =>
  }
}