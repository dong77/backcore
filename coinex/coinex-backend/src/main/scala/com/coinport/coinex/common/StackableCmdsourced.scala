package com.coinport.coinex.common

import akka.persistence.Processor
import com.twitter.scrooge.ThriftStruct
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor.ActorLogging

trait StackableCmdsourced[T <: ThriftStruct, M <: AbstractManager[T]]
    extends Processor with ActorLogging with DumpStateSupport with SnapshotSupport {
  val manager: M

  abstract override def receive = super.receive orElse {
    case cmd: TakeSnapshotNow => takeSnapshot(cmd)(saveSnapshot(manager.getSnapshot))
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case DumpStateToFile => dumpToFile(manager.getSnapshot, self.path.toString.replace("akka://coinex/user", "dump"))
  }
}