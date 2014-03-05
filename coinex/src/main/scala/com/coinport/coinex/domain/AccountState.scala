/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.domain

import scala.collection.immutable.SortedSet

object AccountState {
  type UserAccountsMap = Map[Long, UserAccounts]

  val EmptyUserAccountsMap = Map.empty[Long, UserAccounts]
}
case class AccountState(
  val userAccountsMap: AccountState.UserAccountsMap = AccountState.EmptyUserAccountsMap) {
  
  def getUserAccounts(userId: Long): Option[UserAccounts] = userAccountsMap.get(userId)
  
 // def getUserAccount(userId: Long, currency: Currency): Option[CashAccount] = userAccountsMap.get(userId) map(x => x.cashAccounts.get(currency))
}