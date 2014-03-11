/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.common

import akka.persistence._
import akka.actor._

trait ExtendedProcessor extends Processor with ActorLogging {
  lazy val channel = context.actorOf(PersistentChannel.props(processorId + "_c"), "channel")
  var autoConfirmChannelMessage = true
  var sequenceNr = -1L

  override def preStart() = {
    log.info("============ processorId: {}, channel: {}", processorId, channel.path)
    super.preStart
  }

  def receive = {
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
      else {
        println("msg " + msg + " not defined")
      }
  }

  def keepWhen(conditionEval: => Boolean)(updateState: => Any) = {
    if (conditionEval) updateState
    else currentPersistentMessage.foreach(m => deleteMessage(m.sequenceNr))
  }

  def deliver(msg: Any, dest: ActorPath) = msg match {
    case p: Persistent => channel ! Deliver(p, dest)
    case _ => channel ! Deliver(Persistent(msg), dest)
  }

  def receiveMessage: Receive
}