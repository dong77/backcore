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
import AccountOperationCode._

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef]) extends ExtendedProcessor {
  override val processorId = "coinex_ap"
  val channelToMarketProcessors = createChannelTo("mps") // DO NOT CHANGE

  val manager = new AccountManager()

  def receive = LoggingReceive {
    // ------------------------------------------------------------------------------------------------
    // Snapshots
    case TakeSnapshotNow => saveSnapshot(manager())

    case SaveSnapshotSuccess(metadata) =>

    case SaveSnapshotFailure(metadata, reason) =>

    case SnapshotOffer(meta, snapshot) =>
      log.info("Loaded snapshot {}", meta)
      manager.reset(snapshot.asInstanceOf[AccountState])

    case DebugDump =>
      log.info("state: {}", manager())

    // ------------------------------------------------------------------------------------------------
    // Commands
    case p @ Persistent(DoDepositCash(userId, currency, amount), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))

    case p @ Persistent(DoRequestCashWithdrawal(userId, currency, amount), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, amount))

    case p @ Persistent(DoConfirmCashWithdrawalSuccess(userId, currency, amount), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, 0, 0, -amount))

    case p @ Persistent(DoConfirmCashWithdrawalFailed(userId, currency, amount), seq) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, -amount))

    case p @ Persistent(DoSubmitOrder(side: MarketSide, order @ Order(userId, _, quantity, _, _, _)), seq) =>
      if (quantity <= 0) sender ! AccountOperationResult(InvalidAmount, null)
      else manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0)) match {
        case AccountOperationResult(Ok, _) =>
          val orderWithId = order.copy(id = manager.getAndIncreaseOrderId)
          channelToMarketProcessors forward Deliver(p.withPayload(OrderCashLocked(side, orderWithId)), getProcessorPath(side))
        case m: AccountOperationResult => sender ! m
      }

    // ------------------------------------------------------------------------------------------------
    // From Channel
    case p @ ConfirmablePersistent(OrderSubmissionFailed(side, order, _), seq, _) =>
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
