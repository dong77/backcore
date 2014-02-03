package com.coinport.exchange.roles

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import akka.cluster.ClusterEvent.RoleLeaderChanged

import com.coinport.exchange.common._
import Ids._

class MarketProcessingRoleLeaderActor(market: String) extends Actor with ActorLogging {
  val marketProcessor = context.actorOf(Props(new MarketProcessor(market)), MARKET_PROCESSOR(market))
  def receive = {
    case p @ ConfirmablePersistent(payload, sequenceNr, redeliveries) =>
      log.info("dest: " + payload.toString)
      p.confirm()

    case x => println("othe r" + x)
  }
}

class MarketProcessor(market: String) extends Processor with ActorLogging {
  override def processorId = MARKET_PROCESSOR(market)
  /* private val channelId = BALANCE_TO_MARKET_CHANNEL(market)

  val channel = context.actorOf(PersistentChannel.props(channelId,
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = channelId)
*/
  var destProxy: Option[ActorRef] = None

  def receive = {
    case e: RoleLeaderChanged if e.role == "market_1" =>
    // destProxy foreach context.stop
    // destProxy = e.leader.map { addr =>
    //TODO: construct path in a bettr way?
    // val path = addr.toString + "/user/director/market_1"
    //  context.actorOf(Props(new DestinationProxy(path)))
    // }

    case p @ Persistent(payload, _) =>
      log.info("payload: " + payload.toString)
    // destProxy foreach { actor =>
    // Question: what if the host inside actor.path is down, is there a way for this Persistent to be recovered anyway?
    //  channel ! Deliver(p.withPayload(s"processed ${payload}"), actor.path)
    // }
  }
}