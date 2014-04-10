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

trait AbstractCommandsourced[T <: ThriftStruct, M <: AbstractManager[T]]
    extends Processor with ActorLogging with SnapshotSupport with DumpStateSupport {
  val manager: M
  val channelMap: Map[Class[_], String]

  manager.initFilters(if (channelMap.isEmpty) List("all") else channelMap.values.toList)

  def checkSeen: Actor.Receive = {
    case p @ ConfirmablePersistent(r, seq, _) =>
      val isSeen = if (channelMap.isEmpty) manager.seen("all", seq) else manager.seen(channelMap(r.getClass), seq)
      if (isSeen) {
        log.warning("has been seen the request: ", p)
      } else {
        log.info("not seen the request: ", p)
        super.receive(p)
      }
  }

  abstract override def receive = checkSeen orElse super.receive orElse {
    case cmd: TakeSnapshotNow =>
      log.info(manager.getSnapshot.toString)
      takeSnapshot(cmd)(saveSnapshot(manager.getSnapshot))
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case DumpStateToFile => dumpToFile(manager.getSnapshot, self.path.toString.replace("akka://coinex/user", "dump"))
  }
}

