package com.coinport.coinex

import akka.persistence.SnapshotOffer
import com.coinport.coinex.domain._
import akka.actor.ActorRef
import akka.actor.ActorPath

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef]) extends common.ExtendedProcessor {
  override val processorId = "coinex_ap"

  val manager = new AccountManager()

  override val receiveMessage: Receive = {
    case SnapshotOffer(_, _) =>

    case DepositCash(userId, currency, amount) =>
      manager.depositCash(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case RequestCashWithdrawal(userId, currency, amount) =>
      manager.lockCashForWithdrawal(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case ConfirmCashWithdrawalSuccess(userId, currency, amount) =>
      manager.confirmCashWithdrawal(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case ConfirmCashWithdrawalFailed(userId, currency, amount) =>
      manager.unlockCashForWithdrawal(userId, currency, amount) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) => sender ! AccountOperationOK
      }

    case SubmitOrder(order @ Order(side, data @ OrderData(id, quantity, price, userId))) =>
      manager.lockCash(userId, side.outCurrency, quantity) match {
        case Left(error) => sender ! AccountOperationFailed(error)
        case Right(_) =>

          deliver(OrderSubmitted(order), getProcessorRef(order.side))
      }
  }

  private def getProcessorRef(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}