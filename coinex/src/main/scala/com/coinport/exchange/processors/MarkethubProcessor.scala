package com.coinport.exchange.processors

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import com.coinport.exchange.actors.LocalRouters

class MarkethubProcessor(routers: LocalRouters) extends Processor with ActorLogging {
  override def processorId = "markethub_processor"

  /*
  val balanceChannel = context.actorOf(PersistentChannel.props("markethub_2_balance_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "markethub_2_balance_channel")

  val marketChannel = context.actorOf(PersistentChannel.props("markethub_2_market_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "markethub_2_market_channel")
    */
  var routers: LocalRouters = null

  def receive = {
    case p @ Persistent(payload, _) =>
  }
}
