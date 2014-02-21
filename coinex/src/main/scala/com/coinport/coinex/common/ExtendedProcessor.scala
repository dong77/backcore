package com.coinport.coinex.common

import akka.persistence._
import akka.actor._

case class AdminCommand(cmd: Any)
trait ExtendedProcessor[T] extends Processor with ActorLogging {
  var state: T
  lazy val channel = context.actorOf(PersistentChannel.props(processorId + "_c"), "channel")
  var autoConfirmChannelMessage = true

  override def preStart() = {
    log.info("=== processorId: {}, channel: {}", processorId, channel.path)
    super.preStart
  }

  def receive = {
    case p @ ConfirmablePersistent(payload, seq, _) =>
      autoConfirmChannelMessage = true
      if (receiveMessage.isDefinedAt(p)) receiveMessage(p)
      if (autoConfirmChannelMessage) p.confirm()

    case p: Persistent =>
      if (receiveMessage.isDefinedAt(p)) receiveMessage(p)

    case AdminCommand(cmd) =>
      if (receiveMessage.isDefinedAt(cmd)) receiveMessage(cmd)

    case msg =>
      if (receiveMessage.isDefinedAt(msg)) preserveState(receiveMessage(msg))
  }

  def keepWhen(conditionEval: => Boolean)(updateState: => Any) = {
    val condition = preserveState(conditionEval)
    if (condition) updateState
    else currentPersistentMessage.foreach(m => deleteMessage(m.sequenceNr))
  }

  private def preserveState[T](op: => T): T = {
    val currentState = state
    val result = op
    if (state != currentState) {
      log.error("Attempted to modified state in `prserveState` with message: {}", currentPersistentMessage)
    }
    state = currentState
    result
  }

  def deliver(msg: Any, dest: ActorPath) = msg match {
    case p: Persistent => channel ! Deliver(p, dest)
    case _ => channel ! Deliver(Persistent(msg), dest)
  }

  def receiveMessage: Receive
}