package com.coinport.exchange.views

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._

class BalanceAdminView extends View with ActorLogging {
  override def processorId = "balance_processor"

  def receive = {
    case p @ Persistent(payload, _) =>
  }
}
