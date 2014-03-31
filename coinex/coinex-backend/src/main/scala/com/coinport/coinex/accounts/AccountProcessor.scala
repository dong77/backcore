/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor._
import akka.persistence._
import akka.event.LoggingReceive
import com.coinport.coinex.common._
import Implicits._

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef])
    extends EventsourcedProcessor with ChannelSupport with ActorLogging {
  override val processorId = "coinex_ap"
  val channelToMarketProcessors = createChannelTo("mps") // DO NOT CHANGE

  val manager = new AccountManager()

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {

    case m: DoRequestCashDeposit => persist(m)(updateState)
    case m: DoRequestCashWithdrawal => persist(m)(updateState)
    case m: AdminConfirmCashWithdrawalSuccess => persist(m)(updateState)
    case m: AdminConfirmCashWithdrawalFailure => persist(m)(updateState)

    case m @ DoSubmitOrder(side: MarketSide, order @ Order(userId, _, quantity, _, _, _, _)) =>
      if (quantity <= 0) sender ! SubmitOrderFailed(side, order, ErrorCode.InvalidAmount)
      else persist(m)(updateState)

    case p @ ConfirmablePersistent(event @ OrderSubmitted, seq, _) =>
      persist(event) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(event @ OrderCancelled, seq, _) =>
      persist(event) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(event @ SubmitOrderFailed, seq, _) =>
      persist(event) { event => p.confirm(); updateState(event) }
  }

  def updateState(event: Any): Unit = event match {
    case m @ DoRequestCashDeposit(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))

    case m @ DoRequestCashWithdrawal(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, amount))

    case m @ AdminConfirmCashWithdrawalSuccess(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, 0, 0, -amount))

    case m @ AdminConfirmCashWithdrawalFailure(userId, currency, amount, error) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, -amount))

    case m @ DoSubmitOrder(side: MarketSide, order @ Order(userId, _, quantity, _, _, _, _)) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0)) match {
        case Some(error) => sender ! SubmitOrderFailed(side, order, error)
        case None =>
          val orderWithId = order.copy(id = manager.getAndIncreaseOrderId)
          channelToMarketProcessors forward Deliver(Persistent(OrderFundFrozen(side, orderWithId)), getProcessorPath(side))
      }

    case OrderSubmitted(originOrderInfo, txs) =>
      val side = originOrderInfo.side
      txs foreach { tx =>
        val Transaction(_, _, takerOrderUpdate, makerOrderUpdate) = tx
        manager.sendCash(takerOrderUpdate.userId, makerOrderUpdate.userId, side.outCurrency, takerOrderUpdate.outAmount)
        manager.sendCash(makerOrderUpdate.userId, takerOrderUpdate.userId, side.inCurrency, makerOrderUpdate.outAmount)
        manager.conditionalRefund(takerOrderUpdate.current.hitTakeLimit)(side.outCurrency, takerOrderUpdate.current)
        manager.conditionalRefund(makerOrderUpdate.current.hitTakeLimit)(side.inCurrency, makerOrderUpdate.current)
      }
      // need refund the rest locked currency for the market-price order
      val order = originOrderInfo.order
      originOrderInfo.status match {
        case OrderStatus.MarketAutoCancelled | OrderStatus.MarketAutoPartiallyCancelled =>
          manager.refund(order.userId, side.outCurrency, order.quantity - originOrderInfo.outAmount)
        case _ =>
      }

    case OrderCancelled(side, order) =>
      manager.conditionalRefund(true)(side.outCurrency, order)

    case SubmitOrderFailed(side, order, _) =>
      manager.conditionalRefund(true)(side.outCurrency, order)
  }

  private def getProcessorPath(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
