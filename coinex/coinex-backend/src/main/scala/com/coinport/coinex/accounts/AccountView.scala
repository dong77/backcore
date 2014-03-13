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
      manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))

    case Persistent(DoRequestCashWithdrawal(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, amount))

    case Persistent(DoConfirmCashWithdrawalSuccess(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, 0, 0, -amount))

    case Persistent(DoConfirmCashWithdrawalFailed(userId, currency, amount), _) =>
      manager.updateCashAccount(userId, CashAccount(currency, amount, 0, -amount))

    case Persistent(DoSubmitOrder(side: MarketSide, Order(userId, _, quantity, _)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, -quantity, quantity, 0))

    case Persistent(OrderCancelled(side, Order(userId, _, quantity, _)), _) =>
      manager.updateCashAccount(userId, CashAccount(side.outCurrency, quantity, -quantity, 0))

    case Persistent(marketUpdate @ MarketUpdate, _) =>
      /*marketUpdate.txs foreach { tx =>
        val (taker, maker) = (tx.taker, tx.maker)
        manager.updateCashAccount(taker.userId, CashAccount(taker.currency, 0, -taker.quantity, 0))
        manager.updateCashAccount(taker.userId, CashAccount(maker.currency, maker.quantity, 0, 0))

        manager.updateCashAccount(maker.userId, CashAccount(maker.currency, 0, -maker.quantity, 0))
        manager.updateCashAccount(maker.userId, CashAccount(taker.currency, taker.quantity, 0, 0))
      }*/

    case QueryAccount(userId) =>
      sender ! QueryAccountResult(manager().getUserAccounts(userId))
  }
}