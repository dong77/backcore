/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.actor.ActorPath
import akka.persistence.SnapshotOffer
import akka.persistence._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.data._
import Implicits._

class MarketProcessor(marketSide: MarketSide, accountProcessorPath: ActorPath) extends ExtendedProcessor {
  override val processorId = "coinex_mp_" + marketSide.asString

  val manager = new MarketManager(marketSide)

  def receiveMessage: Receive = {
    // ------------------------------------------------------------------------------------------------
    // Snapshots
    case TakeSnapshotNow =>
      cancelSnapshotSchedule()
      saveSnapshot(manager())
      scheduleSnapshot()

    case SaveSnapshotSuccess(metadata) =>

    case SaveSnapshotFailure(metadata, reason) =>

    case SnapshotOffer(meta, snapshot) =>
      log.info("Loaded snapshot {}", meta)
      manager.reset(snapshot.asInstanceOf[MarketState])

    case DebugDump =>
      log.info("state: {}", manager())
    // ------------------------------------------------------------------------------------------------
    // Commands
    case DoCancelOrder(side, orderId) =>
      manager.removeOrder(side, orderId) foreach { order =>
        deliver(OrderCancelled(side, order), accountProcessorPath)
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case OrderSubmitted(side, order: Order) =>
      val txs = manager.addOrder(side, order)
      if (txs.nonEmpty) {
        deliver(TransactionsCreated(txs), accountProcessorPath)
      }
      sender ! OrderSubmissionDone(side, order, txs)
  }
}