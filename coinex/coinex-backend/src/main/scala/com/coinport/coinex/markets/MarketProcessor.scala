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
  val channelToAccountProcessor = createChannelTo("ap") // DO NOT CHANGE
  val channelToMarketUpdateProcessor = createChannelTo("mup") // DO NOT CHANGE

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

    case QueryActorStats =>
      sender ! manager()

    // ------------------------------------------------------------------------------------------------
    // Commands
    case p @ Persistent(DoCancelOrder(side, orderId, userId), seq) =>
      manager.removeOrder(side, orderId, userId) foreach { order =>
        val cancelled = OrderCancelled(side, order)
        sender ! cancelled
        channelToAccountProcessor forward Deliver(p.withPayload(cancelled), accountProcessorPath)
        channelToMarketUpdateProcessor forward Deliver(p.withPayload(cancelled), marketUpdateProcessoressorPath)
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case p @ ConfirmablePersistent(OrderCashLocked(side, order: Order), seq, _) =>
      p.confirm()
      if (!manager.isOrderPriceInGoodRange(side, order.price)) {
        val event = OrderSubmissionFailed(side, order, OrderSubmissionFailReason.PriceOutOfRange)
        sender ! event
        channelToAccountProcessor ! Deliver(p.withPayload(event), accountProcessorPath)
      } else {
        val orderSubmitted = manager.addOrder(side, order)
        sender ! orderSubmitted
        channelToAccountProcessor ! Deliver(p.withPayload(orderSubmitted), accountProcessorPath)
        channelToMarketUpdateProcessor ! Deliver(p.withPayload(orderSubmitted), marketUpdateProcessoressorPath)
      }
      log.info("state: {}", manager())
  }
}
