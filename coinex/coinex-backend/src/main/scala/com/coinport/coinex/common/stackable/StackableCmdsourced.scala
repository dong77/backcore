package com.coinport.coinex.common.stackable

import akka.persistence.Processor
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor.ActorLogging
import com.coinport.coinex.common.AbstractManager
import com.coinport.coinex.common.support._

trait StackableCmdsourced[T <: AnyRef, M <: AbstractManager[T]]
    extends Processor with ActorLogging with SnapshotSupport with RedeliverFilterSupport[T, M] {
  val manager: M

  abstract override def receive = filterFor(super.receive) orElse super.receive orElse {
    case cmd: TakeSnapshotNow => takeSnapshot(cmd)(saveSnapshot(manager.getSnapshot))
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case DumpStateToFile(_) => sender ! dumpToFile(manager.getSnapshot, self.path)
  }
}
