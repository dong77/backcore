package com.coinport.exchange.processors

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._

import com.coinport.exchange.common._

class BalanceProcessor extends Processor with ActorLogging {
  override def processorId = "balance_processor"

  def receive = {
    case p @ Persistent(payload, _) =>
      log.info("payload: " + payload.toString)
    // refs.markethubProcessor foreach { actor =>
    // Question: what if the host inside actor.path is down, is there a way for this Persistent to be recovered anyway?
    //  channel ! Deliver(p.withPayload(s"processed ${payload}"), actor.path)
    // }
  }

  private def createMarketChannel(market: String): ActorRef = {
    val channelId = "" ///BALANCE_TO_MARKET_CHANNEL(market)

    context.actorOf(PersistentChannel.props(channelId,
      PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
      name = channelId)
  }
}