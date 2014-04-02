/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.event.LoggingReceive
import akka.persistence.Persistent

import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.data._
import Implicits._

class AccountView extends ExtendedView {
  override val processorId = "coinex_ap"
  override val viewId = "coinex_ap_view"
  val manager = new AccountManager()

  def receive = LoggingReceive {
    case _ =>
  }
}
