package com.coinport.coinex.common

import akka.persistence.ConfirmablePersistent
import akka.persistence.EventsourcedProcessor
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.persistence.Processor
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.persistence.Channel
import com.twitter.scrooge.ThriftStruct

import com.coinport.coinex.common.support._
import com.coinport.coinex.data.TakeSnapshotNow

@deprecated(message = "use AbstractManager", since = "20140410")
abstract class Manager[T](s: T) {
  protected var state = s
  def apply(): T = state
  def apply(s: T) = state = s
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

@deprecated(message = "use StackableEventsourced or StackableCmdsourced", since = "20140410")
trait Commandsourced[T, M <: Manager[T]] extends Processor {
  val manager: M

  abstract override def receive = super.receive orElse {
    // TODO(c): need copy a new instance
    case TakeSnapshotNow => saveSnapshot(manager())
    case SnapshotOffer(_, snapshot) => manager(snapshot.asInstanceOf[T])
  }
}
