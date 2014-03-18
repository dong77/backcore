/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor._
import akka.persistence._
import com.coinport.coinex.common.ExtendedProcessor
import Implicits._
import AccountOperationCode._

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef]) extends ExtendedProcessor {
  override val processorId = "coinex_ap"

  val manager = new AccountManager()

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
      manager.reset(snapshot.asInstanceOf[AccountState])

    case DebugDump =>
      log.info("state: {}", manager())

    // ------------------------------------------------------------------------------------------------
    // Commands
    case DoDepositCash(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))

    case DoRequestCashWithdrawal(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, amount))

    case DoConfirmCashWithdrawalSuccess(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, 0, 0, -amount))

    case DoConfirmCashWithdrawalFailed(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, amount, 0, -amount))

    case DoSubmitOrder(side: MarketSide, order @ Order(userId, _, quantity, _, _, _)) =>
      if (quantity <= 0) sender ! AccountOperationResult(InvalidAmount, null)
      else manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0)) match {
        case m @ AccountOperationResult(Ok, _) =>
          val o = order.copy(id = manager.getAndIncreaseOrderId)
          deliver(OrderCashLocked(side, o), getProcessorRef(side))
        case m: AccountOperationResult => sender ! m
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case OrderSubmissionFailed(side, order, _) =>
      manager.conditionalRefund(true)(side.outCurrency, order)

    case OrderCancelled(side, order) =>
      manager.conditionalRefund(true)(side.outCurrency, order)

    case OrderSubmitted(originOrderInfo, txs) =>
      val side = originOrderInfo.side
      txs foreach { tx =>
        val Transaction(_, takerOrderUpdate, makerOrderUpdate) = tx
        manager.sendCash(takerOrderUpdate.userId, makerOrderUpdate.userId, side.outCurrency, takerOrderUpdate.outAmount)
        manager.sendCash(makerOrderUpdate.userId, takerOrderUpdate.userId, side.inCurrency, makerOrderUpdate.outAmount)
        manager.conditionalRefund(takerOrderUpdate.current.hitTakeLimit)(side.outCurrency, takerOrderUpdate.current)
        manager.conditionalRefund(makerOrderUpdate.current.hitTakeLimit)(side.inCurrency, makerOrderUpdate.current)
      }
      val order = originOrderInfo.order
      // need refund the rest locked currency for the market-price order
      order.price foreach { price =>
        if (order.quantity - originOrderInfo.outAmount > 0)
          manager.refund(order.userId, side.outCurrency, order.quantity - originOrderInfo.outAmount)
      }
  }

  private def getProcessorRef(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
