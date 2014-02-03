package com.coinport.exchange.roles

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import akka.cluster.ClusterEvent.RoleLeaderChanged

import com.coinport.exchange.common._
import Ids._

class DepositWithdrawalProcessingRoleLeaderActor extends Actor with ActorLogging {
  val processor = context.actorOf(Props(classOf[DepositWithdrawalProcessor]), DEPOSIT_WITHDRAWAL_PROCESSOR)

  context.system.scheduler.scheduleOnce(10 seconds, processor, Persistent("FOO" + System.currentTimeMillis))(context.dispatcher)
  // context.system.scheduler.scheduleOnce(10 seconds, processor, Persistent("BAR" + System.currentTimeMillis))(context.dispatcher)

  def receive = {
    case msg => processor forward msg
  }
}

class DepositWithdrawalProcessor extends Processor with ActorLogging {
  override def processorId = DEPOSIT_WITHDRAWAL_PROCESSOR
  var destProxy: Option[ActorRef] = None
  val channel = context.actorOf(PersistentChannel.props(DEPOSIT_WITHDRAWAL_TO_BALANCE_CHANNEL,
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = DEPOSIT_WITHDRAWAL_TO_BALANCE_CHANNEL)

  def receive = {
    case e: RoleLeaderChanged if e.role == "balance" =>
      destProxy foreach context.stop
      destProxy = e.leader.map { addr =>
        //TODO: construct path in a bettr way?
        val path = addr.toString + "/user/director/balance"
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
