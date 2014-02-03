package com.coinport.exchange.roles

import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.persistence._
import akka.actor.ActorLogging
import akka.cluster.ClusterEvent.RoleLeaderChanged

import com.coinport.exchange.common._

class BalanceProcessingRoleLeaderActor extends Actor with ActorLogging {
  val processor = context.actorOf(Props(classOf[BalanceProcessor]), "balance_processor")

  context.system.scheduler.scheduleOnce(5 seconds, processor, Persistent("FOO" + System.currentTimeMillis))(context.dispatcher)
  // context.system.scheduler.scheduleOnce(10 seconds, processor, Persistent("BAR" + System.currentTimeMillis))(context.dispatcher)

  def receive = {
    case msg => processor forward msg
  }
}

class BalanceProcessor extends Processor with ActorLogging {
  override def processorId = "balance_processor"

  val channel = context.actorOf(PersistentChannel.props("balance_to_market1_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "balance_to_market1_channel")

  var destProxy: Option[ActorRef] = None

  def receive = {
    case e: RoleLeaderChanged if e.role == "market_1" =>
      destProxy foreach context.stop
      destProxy = e.leader.map { addr =>
        //TODO: construct path in a bettr way?
        val path = addr.toString + "/user/director/market_1"
        context.actorOf(Props(new DestinationProxy(path)))
      }

    case p @ Persistent(payload, _) =>
      log.info("payload: " + payload.toString)
      destProxy foreach { actor =>
        // Question: what if the host inside actor.path is down, is there a way for this Persistent to be recovered anyway?
        channel ! Deliver(p.withPayload(s"processed ${payload}"), actor.path)
      }
  }
}

class MyDestination extends Actor with ActorLogging {
  def receive = {
    case p @ ConfirmablePersistent(payload, sequenceNr, redeliveries) =>
      log.info("dest: " + payload.toString)
      p.confirm()

    case x => println("othe r" + x)
  }
}