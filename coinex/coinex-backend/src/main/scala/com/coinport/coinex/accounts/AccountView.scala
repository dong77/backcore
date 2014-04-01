/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.event.LoggingReceive
import akka.persistence.Persistent

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.data._
import Implicits._

class AccountView extends ExtendedView {
  override val processorId = "coinex_ap"
  override val viewId = "coinex_ap_view"
  val manager = new AccountManager()

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(DoRequestCashDeposit(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))

    case Persistent(DoRequestCashWithdrawal(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, amount))

    case Persistent(AdminConfirmCashWithdrawalSuccess(userId, currency, amount, fees), _) =>
      val amounts = fees.getOrElse(Nil) map { f =>
        manager.sendCashFromWithsrawal(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
        f.amount
      }
      manager.updateCashAccount(userId, CashAccount(currency, 0, 0, (0L /: amounts)(_ + _) - amount))

    case Persistent(AdminConfirmCashWithdrawalFailure(userId, currency, amount, error), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, amount, 0, -amount))

    case Persistent(DoSubmitOrder(side: MarketSide, Order(userId, _, quantity, _, _, _, _, _, _)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0))

    case Persistent(OrderCancelled(side, Order(userId, _, quantity, _, _, _, _, _, _)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, quantity, -quantity, 0))

    case Persistent(m: OrderSubmitted, _) =>
      val side = m.originOrderInfo.side
      m.txs foreach { tx =>
        val Transaction(_, _, _, takerOrderUpdate, makerOrderUpdate, fees) = tx
        manager.sendCashFromLocked(takerOrderUpdate.userId, makerOrderUpdate.userId, side.outCurrency, takerOrderUpdate.outAmount)
        manager.sendCashFromLocked(makerOrderUpdate.userId, takerOrderUpdate.userId, side.inCurrency, makerOrderUpdate.outAmount)
        fees.getOrElse(Nil) foreach { f =>
          manager.sendCashFromValid(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
        }
        manager.conditionalRefund(takerOrderUpdate.current.hitTakeLimit)(side.outCurrency, takerOrderUpdate.current)
        manager.conditionalRefund(makerOrderUpdate.current.hitTakeLimit)(side.inCurrency, makerOrderUpdate.current)
      }
      // need refund the rest locked currency for the market-price order
      val order = m.originOrderInfo.order
      m.originOrderInfo.status match {
        case OrderStatus.MarketAutoCancelled | OrderStatus.MarketAutoPartiallyCancelled =>
          manager.refund(order.userId, side.outCurrency, order.quantity - m.originOrderInfo.outAmount)
        case _ =>
      }

    case QueryAccount(userId) =>
      sender ! QueryAccountResult(manager().getUserAccounts(userId))
  }
}
