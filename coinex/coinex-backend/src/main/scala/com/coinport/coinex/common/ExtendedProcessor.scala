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
  var autoConfirmChannelMessage = true
  val snapshotInterval = 5 minute
  var sequenceNr = -1L

  implicit val ec = context.system.dispatcher
  var cancellable: Cancellable = null

  override def preStart() = {
    log.info("============ processorId: {}, channel: {}", processorId, channel.path)
    super.preStart
    scheduleSnapshot()
  }

  def receive = LoggingReceive {
    case p @ ConfirmablePersistent(payload, seq, _) =>
      sequenceNr = seq
      autoConfirmChannelMessage = true
      if (receiveMessage.isDefinedAt(payload)) receiveMessage(payload)
      if (autoConfirmChannelMessage) p.confirm()

    case p @ Persistent(payload, seq) =>
      sequenceNr = seq
      if (receiveMessage.isDefinedAt(payload)) receiveMessage(payload)

    case msg =>
      sequenceNr = -1L
      if (receiveMessage.isDefinedAt(msg)) receiveMessage(msg)
  }

  protected def keepWhen(conditionEval: => Boolean)(updateState: => Any) = {
    if (conditionEval) updateState
    else currentPersistentMessage.foreach(m => deleteMessage(m.sequenceNr))
  }

  protected def deliver(msg: Any, dest: ActorPath) = msg match {
    case p: Persistent => channel forward Deliver(p, dest)
    case _ => channel forward Deliver(Persistent(msg), dest)
  }

  protected def cancelSnapshotSchedule() = {
    if (cancellable != null && !cancellable.isCancelled) cancellable.cancel()
  }

  protected def scheduleSnapshot() = {
    cancellable = context.system.scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshotNow)
  }

  def receiveMessage: Receive
}