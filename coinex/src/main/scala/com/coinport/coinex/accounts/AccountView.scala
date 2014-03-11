/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import com.coinport.coinex.common.ExtendedView

class AccountView extends ExtendedView {
  override def processorId = "coinex_ap"

  def receive = {
    case _ =>
  }
}