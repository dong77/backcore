package com.coinport.exchange.views

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._

class MarketTransactionView extends View with ActorLogging {
  override def processorId = "x_market_processor"

  def receive = {
    case p @ Persistent(payload, _) =>
  }
}

