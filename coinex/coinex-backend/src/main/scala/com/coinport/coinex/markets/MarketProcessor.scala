/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.actor.ActorPath
import akka.persistence.SnapshotOffer
import akka.persistence._
import akka.event.LoggingReceive
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

  def receive = LoggingReceive {
    // ------------------------------------------------------------------------------------------------
    // Snapshots
    // TODO(c) add global flag to indicate if is snapshoting
    case TakeSnapshotNow => saveSnapshot(manager())

    case SaveSnapshotSuccess(metadata) =>

    case SaveSnapshotFailure(metadata, reason) =>

    case SnapshotOffer(meta, snapshot) =>
      log.info("Loaded snapshot {}", meta)
      manager.reset(snapshot.asInstanceOf[MarketState])

    case DebugDump =>
      log.info("state: {}", manager())

    // ------------------------------------------------------------------------------------------------
    // Commands
    case p @ Persistent(DoCancelOrder(side, orderId), seq) =>
      manager.removeOrder(side, orderId) foreach { order =>
        channel forward Deliver(p.withPayload(OrderCancelled(side, order)), accountProcessorPath)
        channel forward Deliver(p.withPayload(OrderCancelled(side, order)), marketUpdateProcessoressorPath)
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case p @ ConfirmablePersistent(OrderCashLocked(side, order: Order), seq, _) =>
      p.confirm()
      if (!manager.isOrderPriceInGoodRange(side, order.price)) {
        val event = OrderSubmissionFailed(side, order, OrderSubmissionFailReason.PriceOutOfRange)
        sender ! event
        channel ! Deliver(p.withPayload(event), accountProcessorPath)
      } else {
        val orderSubmitted = manager.addOrder(side, order)
        sender ! orderSubmitted

        channel ! Deliver(p.withPayload(orderSubmitted), marketUpdateProcessoressorPath)
        channel ! Deliver(p.withPayload(orderSubmitted), accountProcessorPath)
        log.debug("----------orderSubmitted: {}\n---------- market state: {}", orderSubmitted, manager())
      }
  }
}
