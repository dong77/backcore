package com.coinport.exchange.processors

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._

class MarkethubProcessor extends Processor with ActorLogging {
  override def processorId = "markethub_processor"

  def receive = {
    case p @ Persistent(payload, _) =>
  }
}
