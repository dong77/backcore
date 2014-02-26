package com.coinport.coinex.common

import akka.persistence._
import akka.actor._

trait ExtendedProcessor extends Processor with ActorLogging {
  lazy val channel = context.actorOf(PersistentChannel.props(processorId + "_c"), "channel")
  var autoConfirmChannelMessage = true

  override def preStart() = {
    log.info("============ processorId: {}, channel: {}", processorId, channel.path)
    super.preStart
  }

  def receive = {
    case p @ ConfirmablePersistent(payload, seq, _) =>
      autoConfirmChannelMessage = true
      if (receiveMessage.isDefinedAt(p)) receiveMessage(p)
      if (autoConfirmChannelMessage) p.confirm()

    case p: Persistent =>
      if (receiveMessage.isDefinedAt(p)) receiveMessage(p)

    case msg =>
      if (receiveMessage.isDefinedAt(msg)) receiveMessage(msg)
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