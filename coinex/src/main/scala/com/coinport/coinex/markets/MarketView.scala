/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView

class MarketView(marketSide: MarketSide) extends ExtendedView {
  override def processorId = "coinex_mp_" + marketSide

  def receive = {
    case p @ Persistent(payload, _) => println("view catch up event: " + payload)
    case _ =>
  }
}
