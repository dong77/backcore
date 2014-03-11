/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * MarketManager is the maintainer of a Market. It executes new orders before
 * they are added into a market as pending orders. As execution results, a list
 * of Transactions are generated and returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 *
 * Note this class does NOT depend on event-sourcing framework we choose. Please
 * keep it plain old scala/java.
 */

package com.coinport.coinex.accounts

import com.coinport.coinex.data._
import com.coinport.coinex.common.StateManager

class AccountManager extends StateManager[AccountState] {
  initWithDefaultState(AccountState())

  def depositCash(userId: Long, currency: Currency, amount: Long) =
    updateCashAccount(userId, currency, amount) {
      ca => None
    } {
      ca => ca.copy(available = ca.available + amount)
    }


  def lockCashForWithdrawal(userId: Long, currency: Currency, amount: Long) =
    updateCashAccount(userId, currency, amount) {
      ca => Some(ca.available)
    } {
      ca => ca.copy(available = ca.available - amount, pendingWithdrawal = ca.pendingWithdrawal + amount)
    }

  def unlockCashForWithdrawal(userId: Long, currency: Currency, amount: Long) =
    updateCashAccount(userId, currency, amount) {
      ca => Some(ca.pendingWithdrawal)
    } {
      ca => ca.copy(available = ca.available + amount, pendingWithdrawal = ca.pendingWithdrawal - amount)
    }

  def confirmCashWithdrawal(userId: Long, currency: Currency, amount: Long) =
    updateCashAccount(userId, currency, amount) {
      ca => None
    } {
      ca => ca.copy(pendingWithdrawal = ca.pendingWithdrawal - amount)
    }

  def lockCash(userId: Long, currency: Currency, amount: Long) =
    updateCashAccount(userId, currency, amount) {
      ca => Some(ca.available)
    } {
      ca => ca.copy(available = ca.available - amount, locked = ca.locked + amount)
    }


  def unlockCash(userId: Long, currency: Currency, amount: Long) =
    updateCashAccount(userId, currency, amount) {
      ca => Some(ca.locked)
    } {
      ca => ca.copy(locked = ca.locked - amount, available = ca.available + amount)
    }

  private def updateCashAccount(
    userId: Long,
    currency: Currency,
    amount: Long)(source: CashAccount => Option[Double])(update: CashAccount => CashAccount): Either[AccountOperationCode, CashAccount] = {
    var result: Either[AccountOperationCode, CashAccount] = Left(InvalidAmount)
    if (amount > 0) state = state.adjust(userId, currency) { ca =>
      source(ca) match {
        case Some(s) if s < amount =>
          result = Left(InsuffcientFund)
          None
        case _ =>
          val updated = update(ca)
          result = Right(updated)
          Some(updated)
      }
    }
    result
  }
}