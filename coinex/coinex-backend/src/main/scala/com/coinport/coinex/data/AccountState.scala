/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import scala.collection.immutable.Map
import Implicits._

case class AccountState(userAccountsMap: Map[Long, UserAccount] = Map.empty[Long, UserAccount]) {

  def getUserAccounts(userId: Long): UserAccount =
    userAccountsMap.get(userId).getOrElse(UserAccount(userId))

  def getUserCashAccount(userId: Long, currency: Currency): CashAccount =
    getUserAccounts(userId).cashAccounts.getOrElse(currency, CashAccount(currency, 0, 0, 0))

  def setUserCashAccount(userId: Long, cashAccount: CashAccount): AccountState = {
    if (!cashAccount.isValid)
      throw new IllegalArgumentException("Attempted to set user cash account to an invalid value: " + cashAccount)

    var accounts = userAccountsMap.getOrElse(userId, UserAccount(userId))
    accounts = accounts.copy(cashAccounts = accounts.cashAccounts + (cashAccount.currency -> cashAccount))
    copy(userAccountsMap = userAccountsMap + (userId -> accounts))
  }
}
