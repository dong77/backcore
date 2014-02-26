package com.coinport.coinex

import akka.actor.ActorPath
import akka.persistence.SnapshotOffer
import domain._

class MarketProcessor(marketSide: MarketSide, accountProcessorPath: ActorPath) extends common.ExtendedProcessor {
  override val processorId = "coinex_mp_" + marketSide

  val manager = new MarketManager(marketSide)

  override val receiveMessage: Receive = {
    case SnapshotOffer(_, snapshot) =>
      manager.reset(Some(snapshot.asInstanceOf[Market]))

    case SubmitOrder(order: Order) =>
      val txs = manager.addOrder(order)
      if (txs.nonEmpty) deliver(TransactionsCreated(txs), accountProcessorPath)

    case CancelOrder(orderId) =>
      manager.removeOrder(orderId) foreach { order =>
        deliver(OrderCancelled(order), accountProcessorPath)
      }
  }
}