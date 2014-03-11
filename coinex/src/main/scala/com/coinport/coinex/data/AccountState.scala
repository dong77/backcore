/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

/**
 * available: the amount the user can spend or withdraw.
 * locked: the amount that has been put on lock for pending orders.
 */
case class CashAccount(
  currency: Currency,
  available: Long = 0,
  locked: Long = 0,
  pendingWithdrawal: Long = 0) {
  def total: Long = available + locked + pendingWithdrawal
  def +(another: CashAccount) = {
    if (currency != another.currency)
      throw new IllegalArgumentException("Cannot add different currency accounts")
    CashAccount(currency,
      available + another.available,
      locked + another.locked,
      pendingWithdrawal + another.pendingWithdrawal)
  }

  def -(another: CashAccount) = {
    if (currency != another.currency)
      throw new IllegalArgumentException("Cannot minus different currency accounts")
    CashAccount(currency,
      available - another.available,
      locked - another.locked,
      pendingWithdrawal - another.pendingWithdrawal)
  }

  def isValid = (available >= 0 && locked >= 0 && pendingWithdrawal >= 0)
}

object UserAccount {
  type CashAccounts = Map[Currency, CashAccount]
  val EmptyCashAccounts = Map.empty[Currency, CashAccount]
}

case class UserAccount(
  userId: Long,
  cashAccounts: UserAccount.CashAccounts = UserAccount.EmptyCashAccounts)

object AccountState {
  type UserAccounts = Map[Long, UserAccount]

  val EmptyUserAccounts = Map.empty[Long, UserAccount]
}

case class AccountState(
  val userAccountsMap: AccountState.UserAccounts = AccountState.EmptyUserAccounts) {

  def getUserAccounts(userId: Long): UserAccount =
    userAccountsMap.get(userId).getOrElse(UserAccount(userId))

  def getUserCashAccount(userId: Long, currency: Currency): CashAccount =
    getUserAccounts(userId).cashAccounts.getOrElse(currency, CashAccount(currency))

  def setUserCashAccount(userId: Long, cashAccount: CashAccount): AccountState = {
    if (!cashAccount.isValid) {
      println("warning: attempted to set user cash account to an invalid value: " + cashAccount)
      this
    } else {
      var accounts = userAccountsMap.getOrElse(userId, UserAccount(userId))
      accounts = accounts.copy(cashAccounts = accounts.cashAccounts + (cashAccount.currency -> cashAccount))
      copy(userAccountsMap = userAccountsMap + (userId -> accounts))
    }
  }
}