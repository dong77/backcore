package com.coinport.coinex.common.stackable

import akka.persistence.EventsourcedProcessor
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor.ActorLogging
import com.coinport.coinex.common.AbstractManager
import com.coinport.coinex.common.support._

trait StackableEventsourced[T <: AnyRef, M <: AbstractManager[T]]
    extends EventsourcedProcessor with ActorLogging with SnapshotSupport with RedeliverFilterSupport[T, M] {
  val manager: M
  def updateState(event: Any): Unit

  abstract override def receiveRecover = super.receiveRecover orElse {
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case event: AnyRef => updateState(event)
  }

  abstract override def receiveCommand = filterFor(super.receiveCommand) orElse super.receiveCommand orElse {
    case cmd: TakeSnapshotNow => takeSnapshot(cmd)(saveSnapshot(manager.getSnapshot))
    case DumpStateToFile(_) => sender ! dumpToFile(manager.getSnapshot, self.path)
  }
}
