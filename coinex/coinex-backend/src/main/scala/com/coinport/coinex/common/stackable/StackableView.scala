package com.coinport.coinex.common.stackable

import akka.persistence.View
import akka.persistence.SnapshotOffer
import akka.actor.ActorLogging
import com.coinport.coinex.data._
import com.coinport.coinex.common.AbstractManager
import com.coinport.coinex.common.support._

trait StackableView[T <: AnyRef, M <: AbstractManager[T]]
    extends View with ActorLogging with SnapshotSupport with DumpStateSupport {
  val manager: M

  abstract override def receive = super.receive orElse {
    case cmd: TakeSnapshotNow => takeSnapshot(cmd)(saveSnapshot(manager.getSnapshot))
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case DumpStateToFile => dumpToFile(manager.getSnapshot, self.path.toString.replace("akka://coinex/user", "dump"))
  }
}
