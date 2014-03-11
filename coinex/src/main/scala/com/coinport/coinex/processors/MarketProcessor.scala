/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.processors

import akka.actor.ActorPath
import akka.persistence.SnapshotOffer
import akka.persistence._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.domain._

class MarketProcessor(marketSide: MarketSide, accountProcessorPath: ActorPath) extends ExtendedProcessor {
  override val processorId = "coinex_mp_" + marketSide

  val manager = new MarketManager(marketSide)

  def receiveMessage: Receive = {
    // ------------------------------------------------------------------------------------------------
    // Snapshots
    case SaveSnapshotNow => saveSnapshot(manager() )

    case SaveSnapshotSuccess(metadata) =>

    case SaveSnapshotFailure(metadata, reason) =>

    case SnapshotOffer(meta, snapshot) =>
      log.info("Loaded snapshot {}", meta)
      manager.reset(snapshot.asInstanceOf[MarketState])

    // ------------------------------------------------------------------------------------------------
    // Commands
    case DoCancelOrder(orderId) =>
      manager.removeOrder(orderId) foreach { order =>
        deliver(OrderCancelled(order), accountProcessorPath)
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case OrderSubmitted(order: Order) =>
      val txs = manager.addOrder(order)
      if (txs.nonEmpty) {
        deliver(TransactionsCreated(txs), accountProcessorPath)
      }
      sender ! OrderSubmissionOK(order, txs)
  }
}