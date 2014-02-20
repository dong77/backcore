package dong

import akka.cluster._
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig
import scala.concurrent.duration._
import akka.contrib.pattern.ClusterSingletonManager
import akka.persistence._

object CoinexApp extends App {
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + args(0)).
    withFallback(ConfigFactory.load("application"))
  val system = ActorSystem("coinex", config)

  val p1 = system.actorOf(Props(new P1()), "dest")
  val p2 = system.actorOf(Props(new P2()), "source")

  p2 ! Persistent("to_p1")

}

class P1 extends Processor with ActorLogging {
  val c = context.actorOf(PersistentChannel.props("x"))
  private var autoConfirm = true
  def receive = {
    case p @ ConfirmablePersistent(payload, seq, _) =>
      autoConfirm = true
      if (receiveEvent.isDefinedAt(payload)) receiveEvent(payload)
      if (autoConfirm) p.confirm()
    case p @ Persistent(payload, seq) =>
      if (receiveEvent.isDefinedAt(payload)) receiveEvent(payload)

    case other if (receiveCommand.isDefinedAt(other)) => receiveCommand(other)

  }

  def receiveEvent: Receive = {
    case x =>
  }

  def receiveCommand: Receive = {
    case x =>
  }
}

class P2 extends Processor with ActorLogging {
  val c = context.actorOf(PersistentChannel.props("channel_1"))
  def receive = {
    case p @ Persistent("to_p1", seq) =>
      c ! Deliver(Persistent(p), ActorPath.fromString("akka://coinex/user/dest"))

    case other =>
      log.info("-----" + other)
  }
}
/**
 * > db.messages.find()
 * { "_id" : ObjectId("5306158ec2e61f801d9c0a29"), "processorId" : "/user/source", "sequenceNr" : NumberLong(1), "marker" : "A", "message" : BinData(0,"ChAIARIMrO0ABXQABXRvX3AxEAEaDC91c2VyL3NvdXJjZSAAMABAAFoZYWtrYTovL2NvaW5leC9kZWFkTGV0dGVycw==") }
 * { "_id" : ObjectId("5306158ec2e61f801d9c0a2b"), "processorId" : "/user/source", "sequenceNr" : NumberLong(1), "marker" : "C-channel_1", "message" : BinData(0,"") }
 * { "_id" : ObjectId("5306158ec2e61f801d9c0a2a"), "processorId" : "channel_1", "sequenceNr" : NumberLong(1), "marker" : "A", "message" : BinData(0,"CtYBCAcStwEKmwEKaAgHEkMKEAgBEgys7QAFdAAFdG9fcDEQARoML3VzZXIvc291cmNlIAAwAEAAWhlha2thOi8vY29pbmV4L2RlYWRMZXR0ZXJzGh9ha2thLnBlcnNpc3RlbmNlLlBlcnNpc3RlbnRJbXBsEAEaDC91c2VyL3NvdXJjZSAAMABAAFoZYWtrYTovL2NvaW5leC9kZWFkTGV0dGVycxIXYWtrYTovL2NvaW5leC91c2VyL2Rlc3QaGGFra2EucGVyc2lzdGVuY2UuRGVsaXZlchABGgljaGFubmVsXzEgADAAQABaN2Fra2EudGNwOi8vY29pbmV4QDEyNy4wLjAuMToyNTUxL3VzZXIvc291cmNlIzEzMDMxODg5MzQ=") }
 * { "_id" : ObjectId("5306158ec2e61f801d9c0a2c"), "processorId" : "/user/dest", "sequenceNr" : NumberLong(1), "marker" : "A", "message" : BinData(0,"CmgIBxJDChAIARIMrO0ABXQABXRvX3AxEAEaDC91c2VyL3NvdXJjZSAAMABAAFoZYWtrYTovL2NvaW5leC9kZWFkTGV0dGVycxofYWtrYS5wZXJzaXN0ZW5jZS5QZXJzaXN0ZW50SW1wbBABGgovdXNlci9kZXN0IAAwAEABWjdha2thLnRjcDovL2NvaW5leEAxMjcuMC4wLjE6MjU1MS91c2VyL3NvdXJjZSMxMzAzMTg4OTM0") }
 * 
 * 
 * 
 * > db.messages.find()
 * { "_id" : ObjectId("530615e5c2e66a1be7f8e29d"), "processorId" : "/user/source", "sequenceNr" : NumberLong(1), "marker" : "A", "message" : BinData(0,"ChAIARIMrO0ABXQABXRvX3AxEAEaDC91c2VyL3NvdXJjZSAAMABAAFoZYWtrYTovL2NvaW5leC9kZWFkTGV0dGVycw==") }
 * { "_id" : ObjectId("530615e5c2e66a1be7f8e29f"), "processorId" : "/user/source", "sequenceNr" : NumberLong(1), "marker" : "C-channel_1", "message" : BinData(0,"") }
 * { "_id" : ObjectId("530615e5c2e66a1be7f8e2a0"), "processorId" : "/user/dest", "sequenceNr" : NumberLong(1), "marker" : "A", "message" : BinData(0,"CmgIBxJDChAIARIMrO0ABXQABXRvX3AxEAEaDC91c2VyL3NvdXJjZSAAMABAAFoZYWtrYTovL2NvaW5leC9kZWFkTGV0dGVycxofYWtrYS5wZXJzaXN0ZW5jZS5QZXJzaXN0ZW50SW1wbBABGgovdXNlci9kZXN0IAAwAEABWjdha2thLnRjcDovL2NvaW5leEAxMjcuMC4wLjE6MjU1MS91c2VyL3NvdXJjZSMxNDQwNjE4ODgz") }
 * */

