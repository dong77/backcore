package com.coinport.exchange.roles

import akka.actor._
import akka.persistence.ConfirmablePersistent

class MarketProcessingRoleLeaderActor extends Actor with ActorLogging {
  def receive = {
    case p @ ConfirmablePersistent(payload, sequenceNr, redeliveries) =>
      log.info("dest: " + payload.toString)
      p.confirm()

    case x => println("othe r" + x)
  }
}