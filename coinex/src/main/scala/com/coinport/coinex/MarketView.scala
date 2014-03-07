/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.persistence.Persistent
import com.coinport.coinex.domain._

class MarketView(marketSide: MarketSide) extends common.ExtendedView {
  override def processorId = "coinex_mp_" + marketSide

  def receive = {
    case p @ Persistent(payload, _) => println("view catch up event: " + payload)
    case _ =>
  }
}
