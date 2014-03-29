package com.coinport.coinex.common

import akka.persistence.EventsourcedProcessor
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data.TakeSnapshotNow

abstract class Manager[T](s: T) {
  protected var state = s
  def apply(): T = state
  def apply(s: T) = state = s
}

trait EventCommonProcessing[T, M <: Manager[T]] extends EventsourcedProcessor {
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
