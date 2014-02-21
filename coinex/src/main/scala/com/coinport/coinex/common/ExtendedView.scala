package com.coinport.coinex.common

import akka.actor.ActorLogging
import akka.persistence.View

trait ExtendedView[T] extends View with ActorLogging {
  override def processorId: String
  override lazy val viewId: String = processorId + "_view"
  var state: T

  override def preStart() = {
    log.info("=== processorId: {}, viewId: {}", processorId, viewId)
    super.preStart
  }
}
