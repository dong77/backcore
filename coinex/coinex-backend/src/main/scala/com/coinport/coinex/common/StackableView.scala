package com.coinport.coinex.common

import com.twitter.scrooge.ThriftStruct
import akka.persistence.View
import akka.persistence.SnapshotOffer
import akka.actor.ActorLogging
import com.coinport.coinex.data._

trait StackableView[T <: ThriftStruct, M <: AbstractManager[T]]
    extends View with ActorLogging with DumpStateSupport with SnapshotSupport {
  val manager: M

  abstract override def receive = super.receive orElse {
    case cmd: TakeSnapshotNow => takeSnapshot(cmd)(saveSnapshot(manager.getSnapshot))
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case DumpStateToFile => dumpToFile(manager.getSnapshot, self.path.toString.replace("akka://coinex/user", "dump"))
  }
}