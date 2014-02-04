package com.coinport.exchange.processors
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import com.coinport.exchange.common._

class MarketProcessor(market: String) extends Processor with ActorLogging {
  override def processorId = market + "_market_processor"

  def receive = {
    case p @ Persistent(payload, _) =>
      log.info("payload: " + payload.toString)
  }
}