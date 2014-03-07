/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.domain

import scala.collection.immutable.SortedSet

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

  def getUserAccounts(userId: Long): Option[UserAccount] = userAccountsMap.get(userId)

  def getUserAccount(userId: Long, currency: Currency): Option[CashAccount] =
    userAccountsMap.get(userId) map { _.cashAccounts.getOrElse(currency, null) }

  def adjust(userId: Long, currency: Currency)(update: CashAccount => Option[CashAccount]): AccountState = {
    var accounts = userAccountsMap.getOrElse(userId, UserAccount(userId))
    var cashAccount = accounts.cashAccounts.getOrElse(currency, CashAccount(currency, 0, 0, 0))
    update(cashAccount) match {
      case Some(ca) =>
        accounts = accounts.copy(cashAccounts = accounts.cashAccounts + (currency -> ca))
        copy(userAccountsMap = userAccountsMap + (userId -> accounts))
      case None =>
        this
    }

  }
}