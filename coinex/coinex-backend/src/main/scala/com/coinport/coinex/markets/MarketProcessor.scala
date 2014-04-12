/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.actor.{ ActorRef, ActorPath }
import akka.persistence._
import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.data._
import com.coinport.coinex.data.mutable.MarketState
import Implicits._
import ErrorCode._

class MarketProcessor(
    marketSide: MarketSide,
    accountProcessorPath: ActorPath,
    marketUpdateProcessoressorPath: ActorPath) extends ExtendedProcessor with Processor {

  override def processorId = "coinex_mp_" + marketSide.asString

  val channelToAccountProcessor = createChannelTo("ap") // DO NOT CHANGE
  val channelToMarketUpdateProcessor = createChannelTo("mup") // DO NOT CHANGE
  val manager = new MarketManager(marketSide)

  def receive = LoggingReceive {
    case p @ Persistent(DoCancelOrder(side, orderId, userId), seq) =>
      if (!manager.orderExist(orderId)) {
        sender ! CancelOrderFailed(OrderNotExist)
      } else {
        val order = manager.removeOrder(side, orderId, userId)
        val cancelled = OrderCancelled(side, order)
        sender ! cancelled
        channelToAccountProcessor forward Deliver(p.withPayload(cancelled), accountProcessorPath)
        channelToMarketUpdateProcessor forward Deliver(p.withPayload(cancelled), marketUpdateProcessoressorPath)
      }

    case p @ ConfirmablePersistent(OrderFundFrozen(side, order: Order), seq, _) =>
      p.confirm()
      if (!manager.isOrderPriceInGoodRange(side, order.price)) {
        sender ! SubmitOrderFailed(side, order, PriceOutOfRange)
        val unfrozen = OrderCancelled(side, order)
        channelToAccountProcessor ! Deliver(p.withPayload(unfrozen), accountProcessorPath)
      } else {
        val orderSubmitted = manager.addOrder(side, order)
        sender ! orderSubmitted
        channelToAccountProcessor ! Deliver(p.withPayload(orderSubmitted), accountProcessorPath)
        channelToMarketUpdateProcessor ! Deliver(p.withPayload(orderSubmitted), marketUpdateProcessoressorPath)
      }
  }
}
