package com.coinport.exchange.roles

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import akka.cluster.ClusterEvent.RoleLeaderChanged

import com.coinport.exchange.common._
import Ids._

class BalanceProcessingRoleLeaderActor extends Actor with ActorLogging {
  val processor = context.actorOf(Props(classOf[BalanceProcessor]), BALANCE_PROCESSOR)

  context.system.scheduler.scheduleOnce(10 seconds, processor, Persistent("FOO" + System.currentTimeMillis))(context.dispatcher)
  // context.system.scheduler.scheduleOnce(10 seconds, processor, Persistent("BAR" + System.currentTimeMillis))(context.dispatcher)

  def receive = {
    case msg => processor forward msg
  }
}

class BalanceProcessor extends Processor with ActorLogging {
  override def processorId = BALANCE_PROCESSOR

  val channel = createMarketChannel("1")

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

  private def createMarketChannel(market: String): ActorRef = {
    val channelId = BALANCE_TO_MARKET_CHANNEL(market)

    context.actorOf(PersistentChannel.props(channelId,
      PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
      name = channelId)
  }
}