/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence.SnapshotOffer
import akka.persistence._

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.common._
import com.coinport.coinex.data._
import com.coinport.coinex.fee._
import Implicits._

class AccountProcessor(marketProcessors: Map[MarketSide, ActorRef], val feeConfig: FeeConfig)
    extends EventsourcedProcessor with ChannelSupport with CountFeeSupport with ActorLogging {
  override val processorId = "coinex_ap"
  val channelToMarketProcessors = createChannelTo("mps") // DO NOT CHANGE
  val manager = new AccountManager()

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {

    case m: DoRequestCashDeposit => persist(m)(updateState)
    case m: DoRequestCashWithdrawal => persist(m)(updateState)
    case m: AdminConfirmCashWithdrawalSuccess => persist(countFee(m))(updateState)
    case m: AdminConfirmCashWithdrawalFailure => persist(m)(updateState)

    case m @ DoSubmitOrder(side: MarketSide, order) =>
      if (order.quantity <= 0) sender ! SubmitOrderFailed(side, order, ErrorCode.InvalidAmount)
      else persist(m)(updateState)

    case p @ ConfirmablePersistent(event: OrderSubmitted, seq, _) =>
      persist(countFee(event)) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(event: OrderCancelled, seq, _) =>
      persist(event) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(event: SubmitOrderFailed, seq, _) =>
      persist(event) { event => p.confirm(); updateState(event) }

    case p @ ConfirmablePersistent(AdminConfirmCashDepositSuccess(deposit), seq, _) =>
      p.confirm()
  }

  def updateState(event: Any): Unit = event match {
    case m @ DoRequestCashWithdrawal(withdrawal) =>
      sender ! manager.updateCashAccount(withdrawal.userId, CashAccount(withdrawal.currency, -withdrawal.amount, 0, withdrawal.amount))

    case m @ AdminConfirmCashWithdrawalSuccess(withdrawal, fees) =>
      val amounts = fees.getOrElse(Nil) map { f =>
        manager.sendCashFromWithsrawal(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
        f.amount
      }
      sender ! manager.updateCashAccount(withdrawal.userId, CashAccount(withdrawal.currency, 0, 0, (0L /: amounts)(_ + _) - withdrawal.amount))

    case AdminConfirmCashWithdrawalFailure(withdrawal, error) =>
      sender ! manager.updateCashAccount(withdrawal.userId, CashAccount(withdrawal.currency, withdrawal.amount, 0, -withdrawal.amount))

    case m @ DoSubmitOrder(side: MarketSide, order) =>
      manager.updateCashAccount(order.userId, CashAccount(side.outCurrency, -order.quantity, order.quantity, 0)) match {
        case Some(error) => sender ! SubmitOrderFailed(side, order, error)
        case None =>
          val orderWithId = order.copy(id = manager.getAndIncreaseOrderId)
          channelToMarketProcessors forward Deliver(Persistent(OrderFundFrozen(side, orderWithId)), getProcessorPath(side))
      }

    case OrderSubmitted(originOrderInfo, txs) =>
      val side = originOrderInfo.side
      txs foreach { tx =>
        val (takerOrderUpdate, makerOrderUpdate) = (tx.takerUpdate, tx.makerUpdate)
        val fees = countFee(tx)
        manager.sendCashFromLocked(takerOrderUpdate.userId, makerOrderUpdate.userId, side.outCurrency, takerOrderUpdate.outAmount)
        manager.sendCashFromLocked(makerOrderUpdate.userId, takerOrderUpdate.userId, side.inCurrency, makerOrderUpdate.outAmount)
        tx.fees.getOrElse(Nil) foreach { f =>
          manager.sendCashFromValid(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
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

    case SubmitOrderFailed(side, order, _) =>
      manager.conditionalRefund(true)(side.outCurrency, order)
  }

  private def getProcessorPath(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }
}
