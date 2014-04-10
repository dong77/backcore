package com.coinport.coinex.common

import akka.persistence.EventsourcedProcessor
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data.TakeSnapshotNow
import akka.persistence.Processor
import akka.actor.Actor
import akka.persistence.Channel

abstract class Manager[T](s: T) {
  protected var state = s
  def apply(): T = state
  def apply(s: T) = state = s
}

abstract class AbstractManager[T] {
  def dump: T
  def load(s: T): Unit
}

trait Eventsourced[T, M <: Manager[T]] extends EventsourcedProcessor {
  val manager: M
  def updateState(event: Any): Unit

  abstract override def receiveRecover = super.receiveRecover orElse {
    case SnapshotOffer(_, snapshot) => manager(snapshot.asInstanceOf[T])
    case event: AnyRef => updateState(event)
  }

  abstract override def receiveCommand = super.receiveCommand orElse {
    case TakeSnapshotNow => saveSnapshot(manager())
  }
}

trait Commandsourced[T, M <: Manager[T]] extends Processor {
  val manager: M

  abstract override def receive = super.receive orElse {
    // TODO(c): need copy a new instance
    case TakeSnapshotNow => saveSnapshot(manager())
    case SnapshotOffer(_, snapshot) => manager(snapshot.asInstanceOf[T])
  }
}

trait AbstractCommandsourced[T, M <: AbstractManager[T]] extends Processor {
  val manager: M

  abstract override def receive = super.receive orElse {
    // TODO(c): need copy a new instance
    case TakeSnapshotNow => saveSnapshot(manager.dump)
    case SnapshotOffer(_, snapshot) => manager.load(snapshot.asInstanceOf[T])
  }
}

trait ChannelSupport { self: Actor =>
  def processorId: String

  protected def createChannelTo(dest: String) = {
    val channelName = processorId + "_2_" + dest
    context.actorOf(Channel.props(channelName), channelName)
  }
}

