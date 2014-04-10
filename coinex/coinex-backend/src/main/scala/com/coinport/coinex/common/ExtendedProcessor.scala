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

trait ExtendedProcessor extends Processor with ActorLogging with SnapshotSupport with ChannelSupport {

  val channelMap: Map[Class[_], String] = Map.empty
  override def preStart() = {
    log.info("============ processorId: {}", processorId)
    super.preStart
  }
}
