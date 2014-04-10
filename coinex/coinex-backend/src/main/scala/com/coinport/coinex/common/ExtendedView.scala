/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.common

import akka.actor.ActorLogging
import akka.persistence.View
import com.mongodb.casbah.{ MongoConnection, MongoURI, MongoCollection }

trait ExtendedView extends View with ActorLogging with SnapshotSupport {

  override def preStart() = {
    log.info("------------  processorId: {}, viewId: {}", processorId, viewId)
    super.preStart
  }
}
