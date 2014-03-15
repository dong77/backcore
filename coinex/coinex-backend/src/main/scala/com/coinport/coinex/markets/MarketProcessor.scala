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

class MarketProcessor(
  marketSide: MarketSide,
  accountProcessorPath: ActorPath,
  marketUpdateProcessoressorPath: ActorPath) extends ExtendedProcessor {
  override val processorId = "coinex_mp_" + marketSide.asString

  implicit def timeProvider() = System.currentTimeMillis
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
        deliver(OrderCancelled(side, order), marketUpdateProcessoressorPath)
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case OrderCashLocked(side, order: Order) =>
      val orderSubmitted = manager.addOrder(side, order)
      deliver(orderSubmitted, marketUpdateProcessoressorPath)
      if (orderSubmitted.hasTransaction) {
        deliver(orderSubmitted, accountProcessorPath)
      }

      sender ! orderSubmitted
  }
}