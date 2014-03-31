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
import com.coinport.coinex.common.ExtendedProcessor
import Implicits._

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef]) extends ExtendedProcessor with ActorLogging {
  override val processorId = "coinex_ap"
  val channelToMarketProcessors = createChannelTo("mps") // DO NOT CHANGE

  val manager = new AccountManager()

  def receive = LoggingReceive {
    // ------------------------------------------------------------------------------------------------
    // Commands
    case p @ Persistent(DoRequestCashDeposit(userId, currency, amount), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))
      log.info("state: {}", manager())

    case p @ Persistent(DoRequestCashWithdrawal(userId, currency, amount), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, amount))

    case p @ Persistent(AdminConfirmCashWithdrawalSuccess(userId, currency, amount), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, 0, 0, -amount))

    case p @ Persistent(AdminConfirmCashWithdrawalFailure(userId, currency, amount, error), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, -amount))

    case p @ Persistent(DoSubmitOrder(side: MarketSide, order @ Order(userId, _, quantity, _, _, _, _)), seq) =>
      if (quantity <= 0) sender ! SubmitOrderFailed(side, order, ErrorCode.InvalidAmount)
      else manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0)) match {
        case Some(error) => sender ! SubmitOrderFailed(side, order, error)
        case None =>
          val orderWithId = order.copy(id = manager.getAndIncreaseOrderId)
          channelToMarketProcessors forward Deliver(p.withPayload(OrderFundFrozen(side, orderWithId)), getProcessorPath(side))
      }
      log.info("state: {}", manager())

    // ------------------------------------------------------------------------------------------------
    // From Channel
    case p @ ConfirmablePersistent(SubmitOrderFailed(side, order, _), seq, _) =>
      p.confirm()
      manager.conditionalRefund(true)(side.outCurrency, order)

    case p @ ConfirmablePersistent(OrderCancelled(side, order), seq, _) =>
      p.confirm()
      manager.conditionalRefund(true)(side.outCurrency, order)

    case p @ ConfirmablePersistent(OrderSubmitted(originOrderInfo, txs), seq, _) =>
      p.confirm()
      val side = originOrderInfo.side
      txs foreach { tx =>
        val Transaction(_, takerOrderUpdate, makerOrderUpdate) = tx
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
  }

  private def getProcessorPath(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
