package com.coinport.coinex

import akka.persistence.SnapshotOffer
import com.coinport.coinex.domain._
import akka.actor.ActorRef
import akka.actor.ActorPath
import akka.persistence._

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef]) extends common.ExtendedProcessor {
  override val processorId = "coinex_ap"

  val manager = new AccountManager()

  override val receiveMessage: Receive = {
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
    case cmd @ DoDepositCash(userId, currency, amount) =>
      println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ do deposit cash" + cmd)
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