package com.coinport.exchange.views

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._


class AdminTransferView extends View with ActorLogging {
  override def processorId = "transfer_processor"

  def receive = {
    case _ =>
  }
}

