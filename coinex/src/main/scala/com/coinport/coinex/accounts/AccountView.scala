/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import com.coinport.coinex.common.ExtendedView
import akka.persistence.Persistent
import com.coinport.coinex.data._

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
      manager.updateCashAccount(userId, CashAccount(currency, available = amount))

    case Persistent(DoRequestCashWithdrawal(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, available = -amount, pendingWithdrawal = amount))

    case Persistent(DoConfirmCashWithdrawalSuccess(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, pendingWithdrawal = -amount))

    case Persistent(DoConfirmCashWithdrawalFailed(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, available = amount, pendingWithdrawal = -amount))

    case Persistent(DoSubmitOrder(side: MarketSide, order @ Order(userId, id, quantity, price)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, available = -quantity, locked = quantity))

    case Persistent(e @ OrderCancelled(side, Order(userId, _, quantity, _)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, available = quantity, locked = -quantity))

    case Persistent(TransactionsCreated(txs), _) =>
    case TransactionsCreated(txs) =>
      txs foreach { tx =>
        val (taker, maker) = (tx.taker, tx.maker)
        manager.updateCashAccount(taker.userId, CashAccount(taker.currency, locked = -taker.quantity))
        manager.updateCashAccount(taker.userId, CashAccount(maker.currency, available = maker.quantity))

        manager.updateCashAccount(maker.userId, CashAccount(maker.currency, locked = -maker.quantity))
        manager.updateCashAccount(maker.userId, CashAccount(taker.currency, available = taker.quantity))
      }

    case QueryAccount(userId) =>
      sender ! QueryAccountResult(manager().getUserAccounts(userId))
  }
}