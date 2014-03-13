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
      
    // TODO(d): how to generate id???
    case DoSubmitOrder(side: MarketSide, order @ Order(userId, _, quantity, _, _, _)) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0)) match {
        case m @ AccountOperationResult(Ok, _) =>
          val o = order.copy( id = manager.getAndIncreaseNextOrderId, timestamp = Some(System.currentTimeMillis))
          sender ! OrderSubmissionInProgross(side, o)
          deliver(OrderSubmitted(side, o), getProcessorRef(side))
        case m: AccountOperationResult => sender ! m
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case e @ OrderCancelled(side, Order(userId, _, quantity, _, _, _)) =>
      sender ! manager.updateCashAccount(userId, CashAccount(side.outCurrency, quantity, -quantity, 0))

    case mu: MarketUpdate =>
      mu.txs foreach { tx =>
        val (taker, maker) = (tx.taker, tx.maker)
        manager.updateCashAccount(taker.userId, CashAccount(taker.currency, 0, -taker.quantity, 0))
        manager.updateCashAccount(taker.userId, CashAccount(maker.currency, maker.quantity, 0, 0))

        manager.updateCashAccount(maker.userId, CashAccount(maker.currency, 0, -maker.quantity, 0))
        manager.updateCashAccount(maker.userId, CashAccount(taker.currency, taker.quantity, 0, 0))
      }

      mu.unlockFunds foreach { u =>
        manager.updateCashAccount(u.userId, CashAccount(u.currency, u.amount, -u.amount, 0))
      }

  }

  private def getProcessorRef(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
