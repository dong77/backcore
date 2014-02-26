package com.coinport.coinex

import akka.actor.ActorPath
import akka.persistence.SnapshotOffer
import domain._

class MarketProcessor(marketSide: MarketSide, accountProcessorPath: ActorPath) extends common.ExtendedProcessor {
  override val processorId = "coinex_mp_" + marketSide

  val manager = new MarketManager(marketSide)

  override val receiveMessage: Receive = {
    case SnapshotOffer(_, _) =>
  }
}