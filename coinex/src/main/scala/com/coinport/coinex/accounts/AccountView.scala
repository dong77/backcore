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
      log.info("saw: " + x)
      if (receivex.isDefinedAt(x)) receivex(x)
  }
  def receivex: Receive = {
    case Persistent(DoDepositCash(userId, currency, amount), _) =>
      manager.depositCash(userId, currency, amount)

    case Persistent(DoRequestCashWithdrawal(userId, currency, amount), _) =>
      manager.lockCashForWithdrawal(userId, currency, amount)

    case Persistent(DoConfirmCashWithdrawalSuccess(userId, currency, amount), _) =>
      manager.confirmCashWithdrawal(userId, currency, amount)

    case Persistent(DoConfirmCashWithdrawalFailed(userId, currency, amount), _) =>
      manager.unlockCashForWithdrawal(userId, currency, amount)

    case Persistent(DoSubmitOrder(side: MarketSide, order @ Order(userId, id, quantity, price)), _) =>
      manager.lockCash(userId, side.outCurrency, quantity)

    case Persistent(e @ OrderCancelled(side, Order(userId, _, quantity, _)), _) =>
      manager.unlockCash(userId, side.outCurrency, quantity)

    case Persistent(TransactionsCreated(txs), _) =>
      txs foreach { tx =>
        val (taker, maker) = (tx.taker, tx.maker)
        manager.cleanLocked(taker.userId, taker.currency, taker.quantity)
        manager.cleanLocked(maker.userId, maker.currency, maker.quantity)
        manager.depositCash(taker.userId, maker.currency, maker.quantity)
        manager.depositCash(maker.userId, taker.currency, taker.quantity)
      }

    case QueryAccount(userId) =>
      sender ! QueryAccountResult(manager().getUserAccounts(userId))
  }
}