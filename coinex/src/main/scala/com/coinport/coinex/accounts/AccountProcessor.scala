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

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef]) extends ExtendedProcessor {
  override val processorId = "coinex_ap"

  val manager = new AccountManager()

  def receiveMessage: Receive = {
    // ------------------------------------------------------------------------------------------------
    // Snapshots
    case SaveSnapshotNow => saveSnapshot(manager())

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
      sender ! manager.updateCashAccount(userId, CashAccount(currency, available = amount))

    case DoRequestCashWithdrawal(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, available = -amount, pendingWithdrawal = amount))

    case DoConfirmCashWithdrawalSuccess(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, pendingWithdrawal = -amount))

    case DoConfirmCashWithdrawalFailed(userId, currency, amount) =>
      sender ! manager.updateCashAccount(userId, CashAccount(currency, available = amount, pendingWithdrawal = -amount))

    case DoSubmitOrder(side: MarketSide, order @ Order(userId, id, quantity, price)) =>

      manager.updateCashAccount(userId, CashAccount(side.outCurrency, available = -quantity, locked = quantity)) match {
        case AccountOperationOK => deliver(OrderSubmitted(side, order), getProcessorRef(side))
        case m: AccountOperationFailed => sender ! m
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case e @ OrderCancelled(side, Order(userId, _, quantity, _)) =>
      sender ! manager.updateCashAccount(userId, CashAccount(side.outCurrency, available = quantity, locked = -quantity))

    case TransactionsCreated(txs) =>
      txs foreach { tx =>
        val (taker, maker) = (tx.taker, tx.maker)
        manager.updateCashAccount(taker.userId, CashAccount(taker.currency, locked = -taker.quantity))
        manager.updateCashAccount(taker.userId, CashAccount(maker.currency, available = maker.quantity))

        manager.updateCashAccount(maker.userId, CashAccount(maker.currency, locked = -maker.quantity))
        manager.updateCashAccount(maker.userId, CashAccount(taker.currency, available = taker.quantity))
      }
  }

  private def getProcessorRef(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
