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

trait ExtendedProcessor extends Processor with ActorLogging {
  lazy val channel = context.actorOf(PersistentChannel.props(processorId + "_c"), "channel")
  val snapshotInterval = 30 minute

  implicit val ec = context.system.dispatcher
  var cancellable: Cancellable = null

  override def preStart() = {
    log.info("============ processorId: {}, channel: {}", processorId, channel.path)
    super.preStart
    cancellable = context.system.scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshotNow)
  }

  override def saveSnapshot(snapshot: Any) = {
    if (cancellable != null && !cancellable.isCancelled) cancellable.cancel()
    super.saveSnapshot(snapshot)
    cancellable = context.system.scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshotNow)
  }

  protected def deliver(p: Persistent, dest: ActorPath) =
    {
      println("---------sender: " + sender.path)
      channel forward Deliver(p, dest)
    }

}