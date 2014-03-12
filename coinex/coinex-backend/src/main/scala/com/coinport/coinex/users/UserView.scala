/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.users

import com.coinport.coinex.common.ExtendedView

class UserView extends ExtendedView {
  override def processorId = "coinex_up"

  def receive = {
    case _ =>
  }
}