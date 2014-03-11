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
      manager.depositCash(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case DoRequestCashWithdrawal(userId, currency, amount) =>
      manager.lockCashForWithdrawal(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case DoConfirmCashWithdrawalSuccess(userId, currency, amount) =>
      manager.confirmCashWithdrawal(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case DoConfirmCashWithdrawalFailed(userId, currency, amount) =>
      manager.unlockCashForWithdrawal(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case DoSubmitOrder(side: MarketSide, order @ Order(userId, id, quantity, price)) =>
      manager.lockCash(userId, side.outCurrency, quantity) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => deliver(OrderSubmitted(side, order), getProcessorRef(side))
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case e @ OrderCancelled(side, Order(userId, _, quantity, _)) =>
      manager.unlockCash(userId, side.outCurrency, quantity) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! e
      }

    case TransactionsCreated(txs) =>
      txs foreach { tx =>
        val (taker, maker) = (tx.taker, tx.maker)
        manager.cleanLocked(taker.userId, taker.currency, taker.quantity)
        manager.cleanLocked(maker.userId, maker.currency, maker.quantity)
        manager.depositCash(taker.userId, maker.currency, maker.quantity)
        manager.depositCash(maker.userId, taker.currency, taker.quantity)
      }
  }

  private def getProcessorRef(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
