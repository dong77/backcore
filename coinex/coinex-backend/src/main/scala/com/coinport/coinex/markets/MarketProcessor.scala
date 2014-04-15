/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.actor.{ ActorRef, ActorPath }
import akka.actor.Actor.Receive
import akka.persistence._
import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.data._
import com.coinport.coinex.data.mutable.MarketState
import Implicits._
import ErrorCode._
import com.coinport.coinex.common.support.ChannelSupport
import com.coinport.coinex.common.PersistentId._

class MarketProcessor(
  marketSide: MarketSide,
  accountProcessorPath: ActorPath)
    extends ExtendedProcessor with EventsourcedProcessor with ChannelSupport {

  override def processorId = MARKET_PROCESSOR << marketSide

  val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE

  val manager = new MarketManager(marketSide)

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case m @ DoCancelOrder(_, orderId, userId) =>
      val side = manager.getOrderSide(orderId)
      if (side.isEmpty) {
        sender ! CancelOrderFailed(OrderNotExist)
      } else {
        persist(m.copy(side = side.get))(updateState)
      }

    case p @ ConfirmablePersistent(m @ OrderFundFrozen(side, order: Order), seq, _) =>
      p.confirm()
      if (!manager.isOrderPriceInGoodRange(side, order.price)) {
        sender ! SubmitOrderFailed(side, order, PriceOutOfRange)
        val unfrozen = OrderCancelled(side, order)
        channelToAccountProcessor forward Deliver(p.withPayload(unfrozen), accountProcessorPath)
      } else {
        persist(m)(updateState)
      }
  }

  def updateState: Receive = {
    case DoCancelOrder(side, orderId, userId) =>
      val order = manager.removeOrder(side, orderId, userId)
      val cancelled = OrderCancelled(side, order)
      channelToAccountProcessor forward Deliver(Persistent(cancelled), accountProcessorPath)

    case OrderFundFrozen(side, order: Order) =>
      val orderSubmitted = manager.addOrder(side, order)
      channelToAccountProcessor forward Deliver(Persistent(orderSubmitted), accountProcessorPath)
  }
}
