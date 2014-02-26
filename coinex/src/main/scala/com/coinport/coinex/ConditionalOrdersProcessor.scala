package com.coinport.coinex

import akka.actor.ActorPath
import akka.persistence.SnapshotOffer
import domain._

class ConditionalOrdersProcessor(marketSide: MarketSide, marketPricessorPath: ActorPath)
  extends common.ExtendedProcessor {
  override val processorId = "coinex_cop_" + marketSide

  val manager = new ConditionalOrdersManager(marketSide)

  override val receiveMessage: Receive = {
    case SnapshotOffer(_, snapshot) =>
      manager.reset(snapshot.asInstanceOf[ConditionalOrdersState])

    case NewTxPrice(marketSide, price) =>
      val orders = manager.updateWithNewPrice(marketSide, price)
      deliver(OrdersTriggered(orders), marketPricessorPath)
  }
}