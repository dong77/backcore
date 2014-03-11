/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.processors

import akka.persistence.SnapshotOffer
import com.coinport.coinex.domain._
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

    case DoSubmitOrder(order @ Order(side, data @ OrderData(id, quantity, price, userId))) =>
      manager.lockCash(userId, side.outCurrency, quantity) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) =>
          deliver(OrderSubmitted(order), getProcessorRef(order.side))
      }

    // ------------------------------------------------------------------------------------------------
    // Events
    case OrderCancelled(order: Order) =>
  }

  private def getProcessorRef(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
