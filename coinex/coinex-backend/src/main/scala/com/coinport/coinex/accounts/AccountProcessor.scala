/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence.SnapshotOffer
import akka.persistence._

import com.coinport.coinex.common._
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.support._
import com.coinport.coinex.data._
import com.coinport.coinex.fee._
import ErrorCode._
import Implicits._

class AccountProcessor(
  marketProcessors: Map[MarketSide, ActorRef],
  depositWithdrawProcessorPath: ActorPath,
  val feeConfig: FeeConfig) extends ExtendedProcessor with EventsourcedProcessor with ChannelSupport
    with AccountManagerBehavior with ActorLogging {
  override val processorId = "coinex_ap"
  val channelToMarketProcessors = createChannelTo("mps") // DO NOT CHANGE
  val channelToDepositWithdrawalProcessor = createChannelTo("dwp") // DO NOT CHANGE
  val manager = new AccountManager()

  override def identifyChannel: PartialFunction[Any, String] = {
    case r: AdminConfirmCashWithdrawalSuccess => "dwp"
    case r: AdminConfirmCashWithdrawalFailure => "dwp"
    case r: AdminConfirmCashDepositSuccess => "dwp"
    case r: OrderSubmitted => "mp"
    case r: OrderCancelled => "mp"
  }

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case m @ DoRequestCashWithdrawal(w) =>
      val adjustment = CashAccount(w.currency, -w.amount, 0, w.amount)
      if (!manager.canUpdateCashAccount(w.userId, adjustment)) {
        sender ! RequestCashWithdrawalFailed(InsufficientFund)
      } else {
        val updated = countFee(w.copy(id = lastSequenceNr, created = Some(System.currentTimeMillis)))
        persist(DoRequestCashWithdrawal(updated)) { event =>
          updateState(event)
          channelToDepositWithdrawalProcessor forward Deliver(Persistent(event), depositWithdrawProcessorPath)
          sender ! RequestCashWithdrawalSucceeded(updated)
        }
      }

    case m @ DoRequestCashDeposit(deposit) =>
      if (deposit.amount <= 0) {
        sender ! RequestCashDepositFailed(InvalidAmount)
      } else {
        val updated = countFee(deposit.copy(id = lastSequenceNr, created = Some(System.currentTimeMillis)))
        persist(m.copy(deposit = updated)) { event =>
          updateState(event)
          channelToDepositWithdrawalProcessor forward Deliver(Persistent(event), depositWithdrawProcessorPath)
          sender ! RequestCashDepositSucceeded(updated)
        }
      }

    case p @ ConfirmablePersistent(m: AdminConfirmCashWithdrawalSuccess, seq, _) =>
      persist(m) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(m: AdminConfirmCashWithdrawalFailure, seq, _) =>
      persist(m) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(m: AdminConfirmCashDepositSuccess, seq, _) =>
      persist(m) { event => p.confirm(); updateState(event) }

    case DoSubmitOrder(side, order) =>
      if (order.quantity <= 0) {
        sender ! SubmitOrderFailed(side, order, ErrorCode.InvalidAmount)
      } else {
        val adjustment = CashAccount(side.outCurrency, -order.quantity, order.quantity, 0)
        if (!manager.canUpdateCashAccount(order.userId, adjustment)) {
          sender ! SubmitOrderFailed(side, order, ErrorCode.InsufficientFund)
        } else {
          val updated = order.copy(id = lastSequenceNr, timestamp = Some(System.currentTimeMillis))
          persist(DoSubmitOrder(side, updated)) { event =>
            channelToMarketProcessors forward Deliver(Persistent(OrderFundFrozen(side, updated)), getProcessorPath(side))
            updateState(event)
          }
        }
      }

    case p @ ConfirmablePersistent(event: OrderSubmitted, seq, _) =>
      persist(countFee(event)) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(event: OrderCancelled, seq, _) =>
      persist(event) { event => p.confirm(); updateState(event) }
  }

  private def getProcessorPath(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}

trait AccountManagerBehavior extends CountFeeSupport {
  val manager: AccountManager

  def updateState(event: Any): Unit = event match {
    case m: DoRequestCashDeposit => // do nothing
    case DoRequestCashWithdrawal(w) => manager.updateCashAccount(w.userId, CashAccount(w.currency, -w.amount, 0, w.amount))
    case AdminConfirmCashDepositSuccess(d) =>
      manager.updateCashAccount(d.userId, CashAccount(d.currency, d.amount, 0, 0))
      d.fee match {
        case Some(f) if (f.amount > 0) =>
          manager.transferFundFromAvailable(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
        case _ => None
      }
    case AdminConfirmCashWithdrawalSuccess(w) =>
      w.fee match {
        case Some(f) if (f.amount > 0) =>
          manager.transferFundFromPendingWithdrawal(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
          manager.updateCashAccount(w.userId, CashAccount(w.currency, 0, 0, f.amount - w.amount))
        case _ =>
          manager.updateCashAccount(w.userId, CashAccount(w.currency, 0, 0, -w.amount))
      }
    case AdminConfirmCashWithdrawalFailure(w, _) => manager.updateCashAccount(w.userId, CashAccount(w.currency, w.amount, 0, -w.amount))

    case DoSubmitOrder(side: MarketSide, order) =>
      manager.updateCashAccount(order.userId, CashAccount(side.outCurrency, -order.quantity, order.quantity, 0))

    case OrderSubmitted(originOrderInfo, txs) =>
      val side = originOrderInfo.side
      txs foreach { tx =>
        val (takerOrderUpdate, makerOrderUpdate, fees) = (tx.takerUpdate, tx.makerUpdate, tx.fees)
        manager.transferFundFromLocked(takerOrderUpdate.userId, makerOrderUpdate.userId, side.outCurrency, takerOrderUpdate.outAmount)
        manager.transferFundFromLocked(makerOrderUpdate.userId, takerOrderUpdate.userId, side.inCurrency, makerOrderUpdate.outAmount)
        tx.fees.getOrElse(Nil) foreach { f =>
          manager.transferFundFromAvailable(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
        }
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
  }
}
