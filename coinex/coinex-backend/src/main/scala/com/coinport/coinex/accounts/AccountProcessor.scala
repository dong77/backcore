/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.actor._
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import akka.persistence.SnapshotOffer
import akka.persistence._

import com.coinport.coinex.common._
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.support._
import com.coinport.coinex.data._
import com.coinport.coinex.fee._
import ErrorCode._
import Implicits._

class AccountProcessor(
  marketProcessors: Map[MarketSide, ActorRef],
  marketUpdateProcessoressorPath: ActorPath,
  depositWithdrawProcessorPath: ActorPath,
  val feeConfig: FeeConfig) extends ExtendedProcessor with EventsourcedProcessor with ChannelSupport
    with AccountManagerBehavior with ActorLogging {

  override val processorId = ACCOUNT_PROCESSOR <<
  val channelToMarketProcessors = createChannelTo(MARKET_PROCESSOR <<) // DO NOT CHANGE
  val channelToMarketUpdateProcessor = createChannelTo(MARKET_UPDATE_PROCESSOR<<) // DO NOT CHANGE
  val channelToDepositWithdrawalProcessor = createChannelTo(DEPOSIT_WITHDRAW_PROCESSOR<<) // DO NOT CHANGE
  val manager = new AccountManager()

  override def identifyChannel: PartialFunction[Any, String] = {
    case r: AdminConfirmCashWithdrawalSuccess => "dwp"
    case r: AdminConfirmCashWithdrawalFailure => "dwp"
    case r: AdminConfirmCashDepositSuccess => "dwp"
    case OrderSubmitted(originOrderInfo, txs) => "mp_" + originOrderInfo.side.s
    case OrderCancelled(side, order) => "mp_" + side.s
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
      persist(countFee(event)) { event =>
        p.confirm()
        sender ! event
        updateState(event)
        channelToMarketUpdateProcessor forward Deliver(Persistent(event), marketUpdateProcessoressorPath)
      }

    case p @ ConfirmablePersistent(event: OrderCancelled, seq, _) =>
      persist(countFee(event)) { event =>
        p.confirm()
        sender ! event
        updateState(event)
        channelToMarketUpdateProcessor forward Deliver(Persistent(event), marketUpdateProcessoressorPath)
      }
  }

  private def getProcessorPath(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}

trait AccountManagerBehavior extends CountFeeSupport {
  val manager: AccountManager

  def updateState: Receive = {
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

        manager.conditionalRefund(takerOrderUpdate.current.refundReason != None)(side.outCurrency, takerOrderUpdate.current)
        manager.conditionalRefund(makerOrderUpdate.current.refundReason != None)(side.inCurrency, makerOrderUpdate.current)
      }
      val order = originOrderInfo.order
      if (txs.size == 0 && order.refundReason != None)
        manager.refund(order.userId, side.outCurrency, order.quantity - originOrderInfo.outAmount)

    case OrderCancelled(side, order) =>
      manager.conditionalRefund(true)(side.outCurrency, order)
  }
}
