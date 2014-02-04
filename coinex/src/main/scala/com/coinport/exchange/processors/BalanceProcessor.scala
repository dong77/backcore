package com.coinport.exchange.processors

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import com.coinport.exchange.common._
import com.coinport.exchange.actors.LocalRouters

class BalanceProcessor extends Processor with ActorLogging {
  override def processorId = "balance_processor"

  val markethubChannel = context.actorOf(PersistentChannel.props("balance_2_markethub_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "balance_2_markethub_channel")

  val transferChannel = context.actorOf(PersistentChannel.props("balance_2_transfer_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "balance_2_transfer_channel")

  var routers: LocalRouters = null

  def receive = {
    case routers: LocalRouters =>
      this.routers = routers

    case p @ Persistent(payload, _) =>
      log.info("payload: " + payload.toString)
    // refs.markethubProcessor foreach { actor =>
    // Question: what if the host inside actor.path is down, is there a way for this Persistent to be recovered anyway?
    //  channel ! Deliver(p.withPayload(s"processed ${payload}"), actor.path)
    // }
  }
}