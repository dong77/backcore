package com.coinport.coinex.common.stackable

import akka.persistence.Processor
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor.ActorLogging
import com.coinport.coinex.common.AbstractManager
import com.coinport.coinex.common.support._

trait StackableCmdsourced[T <: AnyRef, M <: AbstractManager[T]]
    extends Processor with ActorLogging with SnapshotSupport with DumpStateSupport with RedeliverFilterSupport[T, M] {
  val manager: M

  override protected def handleUnseen = super.receive

  abstract override def receive = checkSeen orElse super.receive orElse {
    case cmd: TakeSnapshotNow => takeSnapshot(cmd)(saveSnapshot(manager.getSnapshot))
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case DumpStateToFile => dumpToFile(manager.getSnapshot, self.path.toString.replace("akka://coinex/user", "dump"))
  }
}
