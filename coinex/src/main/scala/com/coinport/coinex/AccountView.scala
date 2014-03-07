package com.coinport.coinex

import com.coinport.coinex.messages.Bonk

class AccountView extends common.ExtendedView {
  override def processorId = "coinex_ap"

  def receive = {
    case _ =>
  }
}