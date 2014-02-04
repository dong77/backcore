package com.coinport.exchange.processors

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._

import com.coinport.exchange.common._

class TransferProcessor extends Processor with ActorLogging {
  override def processorId = "transfer_processor"

  def receive = {
    case p @ Persistent(payload, _) =>
      log.info("payload: " + payload.toString)
    // refs.balanceProcessor foreach { actor =>
    //channel ! Deliver(p.withPayload(s"processed ${payload}"), actor.path)
    //}
  }
}
