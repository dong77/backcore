package com.coinport.exchange.processors
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import com.coinport.exchange.common._
import com.coinport.exchange.actors.LocalRouters

// NOT USED YET
class MarketProcessor(market: String, routers: LocalRouters) extends Processor with ActorLogging {
  override def processorId = market + "_market_processor"

  /*
  
  val markethubChannel = context.actorOf(PersistentChannel.props("market_2_markethub_channel",
    PersistentChannelSettings(redeliverInterval = 3 seconds, redeliverMax = 15)),
    name = "market_2_markethub_channel")*/

  def receive = {
    case p @ Persistent(payload, _) =>
      log.info("payload: " + payload.toString)
  }
}