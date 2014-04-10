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

@deprecated(message = "use AbstractEventsourced or AbstractCommandsourced", since = "20140410")
trait ExtendedProcessor extends Processor with ActorLogging {
  // val snapshotInterval = 3 minute
  val snapshotInterval = 10 second

  implicit val ec = context.system.dispatcher
  private var cancellable: Cancellable = null

  override def preStart() = {
    log.info("============ processorId: {}", processorId)
    super.preStart
    scheduleSnapshot()
  }

  override def saveSnapshot(snapshot: Any) = {
    cancelSnapshot()
    super.saveSnapshot(snapshot)
    scheduleSnapshot()
  }

  protected def cancelSnapshot() =
    if (cancellable != null && !cancellable.isCancelled) cancellable.cancel()

  protected def scheduleSnapshot() =
    cancellable = context.system.scheduler.scheduleOnce(snapshotInterval, self, TakeSnapshotNow("scheduled"))

  protected def createChannelTo(dest: String) = {
    val channelName = processorId + "_2_" + dest
    context.actorOf(Channel.props(channelName), channelName)
  }
}
