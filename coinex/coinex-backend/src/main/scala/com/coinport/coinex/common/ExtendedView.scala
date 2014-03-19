/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.common

import akka.actor.ActorLogging
import akka.persistence.View

trait ExtendedView extends View with ActorLogging {

  override def preStart() = {
    log.info("------------  processorId: {}, viewId: {}", processorId, viewId)
    super.preStart
  }
}
