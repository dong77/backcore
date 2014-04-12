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
import com.coinport.coinex.common.support.ChannelSupport

class MarketProcessor(
  marketSide: MarketSide,
  accountProcessorPath: ActorPath,
  marketUpdateProcessoressorPath: ActorPath)
    extends ExtendedProcessor with EventsourcedProcessor with ChannelSupport {

  override def processorId = "coinex_mp_" + marketSide.s

  val channelToAccountProcessor = createChannelTo("ap") // DO NOT CHANGE
  val channelToMarketUpdateProcessor = createChannelTo("mup") // DO NOT CHANGE
  val manager = new MarketManager(marketSide)

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case m @ DoCancelOrder(side, orderId, userId) =>
      if (!manager.orderExist(orderId)) {
        sender ! CancelOrderFailed(OrderNotExist)
      } else {
        persist(m)(updateState)
      }

    case p @ ConfirmablePersistent(m @ OrderFundFrozen(side, order: Order), seq, _) =>
      p.confirm()
      if (!manager.isOrderPriceInGoodRange(side, order.price)) {
        sender ! SubmitOrderFailed(side, order, PriceOutOfRange)
        val unfrozen = OrderCancelled(side, order)
        channelToAccountProcessor ! Deliver(p.withPayload(unfrozen), accountProcessorPath)
      } else {
        persist(m)(updateState)
      }
  }

  def updateState(event: Any): Unit = event match {
    case DoCancelOrder(side, orderId, userId) =>
      val order = manager.removeOrder(side, orderId, userId)
      val cancelled = OrderCancelled(side, order)
      sender ! cancelled
      channelToAccountProcessor forward Deliver(Persistent(cancelled), accountProcessorPath)
      channelToMarketUpdateProcessor forward Deliver(Persistent(cancelled), marketUpdateProcessoressorPath)

    case OrderFundFrozen(side, order: Order) =>
      val orderSubmitted = manager.addOrder(side, order)
      sender ! orderSubmitted
      channelToAccountProcessor ! Deliver(Persistent(orderSubmitted), accountProcessorPath)
      channelToMarketUpdateProcessor ! Deliver(Persistent(orderSubmitted), marketUpdateProcessoressorPath)
  }
}
