package com.coinport.coinex

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import scala.collection.mutable
import java.util.Random
import scala.util.Sorting
import Domain._
import scala.annotation.tailrec

class MarketProcessor(market: Market, accountProcessorPath: ActorPath)
  extends common.ExtendedProcessor[MarketProcessorState] with ActorLogging {
  override val processorId = "coinex_market_processor_" + market
  println("============market processor created: " + self.path)

  var state = new MarketProcessorState(market)

  override val receiveMessage: Receive = {
    case SnapshotOffer(_, _) =>

    case DebugDump => println("-" * 100 + "\n" + state.toString)
    case DebugResetState => state = new MarketProcessorState(market)

    case SubmitOrder(o: BuyOrder) =>
      println("---buy order submitted" + o)

    case SubmitOrder(o: SellOrder) =>
      println("---sell order submitted" + o)

    case cmd @ CancelOrder(o: BuyOrder) => keepWhen {
      o.id >= 0
    } {
      deliver(cmd, accountProcessorPath)
    }

    case cmd @ CancelOrder(o: SellOrder) =>
      deliver(cmd, accountProcessorPath)
  }
}

// if market is Market(BTC, RMB), then we call orders with Market(BTC, RMB) buy orders
class MarketProcessorState(market: Market) {
}