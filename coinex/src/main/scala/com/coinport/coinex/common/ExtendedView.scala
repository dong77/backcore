package com.coinport.coinex.common

import akka.actor.ActorLogging
import akka.persistence.View

trait ExtendedView extends View with ActorLogging {
  override def processorId: String
  override lazy val viewId = processorId + "_view"

  override def preStart() = {
    log.info("------------  processorId: {}, viewId: {}", processorId, viewId)
    super.preStart
  }
}
