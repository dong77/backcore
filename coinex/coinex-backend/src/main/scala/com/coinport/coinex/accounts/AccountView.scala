/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import com.coinport.coinex.common.ExtendedView
import akka.persistence.Persistent
import com.coinport.coinex.data._
import Implicits._

class AccountView extends ExtendedView {
  override def processorId = "coinex_ap"
  val manager = new AccountManager()
  def receive = {
    case DebugDump =>
      log.info("state: {}", manager())
    case x =>
      log.info("~~~ saw: " + x)
      if (receiveMessage.isDefinedAt(x)) receiveMessage(x)
  }

  def receiveMessage: Receive = {
    case Persistent(DoDepositCash(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))

    case Persistent(DoRequestCashWithdrawal(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, amount))

    case Persistent(DoConfirmCashWithdrawalSuccess(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, 0, 0, -amount))

    case Persistent(DoConfirmCashWithdrawalFailed(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, amount, 0, -amount))

    case Persistent(DoSubmitOrder(side: MarketSide, Order(userId, _, quantity, _, _, _)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0))

    case Persistent(OrderCancelled(side, Order(userId, _, quantity, _, _, _)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, quantity, -quantity, 0))

    case Persistent(m: OrderSubmitted, _) =>
      val side = m.originOrderInfo.side
      m.txs foreach { tx =>
        val Transaction(_, takerOrderUpdate, makerOrderUpdate) = tx
        manager.sendCash(takerOrderUpdate.userId, makerOrderUpdate.userId, side.outCurrency, takerOrderUpdate.outAmount)
        manager.sendCash(makerOrderUpdate.userId, takerOrderUpdate.userId, side.inCurrency, makerOrderUpdate.outAmount)
        manager.conditionalRefund(takerOrderUpdate.current.hitTakeLimit)(side.outCurrency, takerOrderUpdate.current)
        manager.conditionalRefund(makerOrderUpdate.current.hitTakeLimit)(side.inCurrency, makerOrderUpdate.current)
      }

    case QueryAccount(userId) =>
      sender ! QueryAccountResult(manager().getUserAccounts(userId))
  }
}