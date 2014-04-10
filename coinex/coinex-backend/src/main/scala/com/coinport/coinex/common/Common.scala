package com.coinport.coinex.common

import akka.persistence.EventsourcedProcessor
import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.persistence.Processor
import akka.actor.Actor
import akka.persistence.Channel
import com.twitter.scrooge.ThriftStruct

@deprecated(message = "use AbstractManager", since = "20140410")
abstract class Manager[T](s: T) {
  protected var state = s
  def apply(): T = state
  def apply(s: T) = state = s
}

abstract class AbstractManager[T <: ThriftStruct] {
  def getSnapshot: T
  def loadSnapshot(s: T): Unit
}

@deprecated(message = "use AbstractEventsourced or AbstractCommandsourced", since = "20140410")
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

@deprecated(message = "use AbstractEventsourced or AbstractCommandsourced", since = "20140410")
trait Commandsourced[T, M <: Manager[T]] extends Processor {
  val manager: M

  abstract override def receive = super.receive orElse {
    // TODO(c): need copy a new instance
    case TakeSnapshotNow => saveSnapshot(manager())
    case SnapshotOffer(_, snapshot) => manager(snapshot.asInstanceOf[T])
  }
}

trait AbstractEventsourced[T <: ThriftStruct, M <: AbstractManager[T]] extends EventsourcedProcessor with DumpStateSupport {
  val manager: M
  def updateState(event: Any): Unit

  abstract override def receiveRecover = super.receiveRecover orElse {
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case event: AnyRef => updateState(event)
  }

  abstract override def receiveCommand = super.receiveCommand orElse {
    case TakeSnapshotNow => saveSnapshot(manager.getSnapshot)
    case DumpStateToFile => dumpToFile(manager.getSnapshot, self.path.toString.replace("akka://coinex/user", "dump"))
  }
}

trait AbstractCommandsourced[T <: ThriftStruct, M <: AbstractManager[T]] extends Processor with DumpStateSupport {
  val manager: M

  abstract override def receive = super.receive orElse {
    // TODO(c): need copy a new instance
    case TakeSnapshotNow => saveSnapshot(manager.getSnapshot)
    case SnapshotOffer(_, snapshot) => manager.loadSnapshot(snapshot.asInstanceOf[T])
    case DumpStateToFile => dumpToFile(manager.getSnapshot, self.path.toString.replace("akka://coinex/user", "dump"))
  }
}

trait ChannelSupport { self: Actor =>
  def processorId: String

  protected def createChannelTo(dest: String) = {
    val channelName = processorId + "_2_" + dest
    context.actorOf(Channel.props(channelName), channelName)
  }
}

