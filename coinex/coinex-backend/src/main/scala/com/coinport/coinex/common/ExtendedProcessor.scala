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
import com.coinport.coinex.common.support._

trait ExtendedProcessor extends Actor with ActorLogging with SnapshotSupport with ChannelSupport {

  def identifyChannel: PartialFunction[Any, String] = PartialFunction.empty

  val snapshotIntervalSec = 30

  override def preStart() = {
    log.info("============ processorId: {}", processorId)
    super.preStart
  }
}
