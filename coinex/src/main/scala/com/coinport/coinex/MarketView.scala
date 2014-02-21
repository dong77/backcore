package com.coinport.coinex

import Domain.Market
import akka.persistence.Persistent

class MarketView(market: Market) extends common.ExtendedView[MarketViewState] {
  override def processorId = "coinex_market_processor_" + market
  var state = new MarketViewState()

  def receive = {
    case p @ Persistent(payload, _) => println("view catch up event: " + payload)
    case _ =>
  }

}

case class MarketViewState 