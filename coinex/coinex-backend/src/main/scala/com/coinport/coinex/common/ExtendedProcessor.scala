/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.common

import akka.persistence._
import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import scala.concurrent.duration._
import com.coinport.coinex.data.TakeSnapshotNow

trait ExtendedProcessor extends Processor with ActorLogging with SnapshotSupport with ChannelSupport {

  override def preStart() = {
    log.info("============ processorId: {}", processorId)
    super.preStart
  }
}
