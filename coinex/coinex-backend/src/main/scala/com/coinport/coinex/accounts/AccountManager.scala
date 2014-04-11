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

import scala.collection.mutable.Map
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import Implicits._

class AccountManager extends AbstractManager[TAccountState] {
  // Internal mutable state ----------------------------------------------
  private val accountMap: Map[Long, UserAccount] = Map.empty[Long, UserAccount]

  // Thrift conversions     ----------------------------------------------
  def getSnapshot = TAccountState(accountMap.clone, getFiltersSnapshot)

  def loadSnapshot(snapshot: TAccountState) = {
    accountMap.clear
    accountMap ++= snapshot.userAccountsMap
    loadFiltersSnapshot(snapshot.filters)
  }

  // Business logics      ----------------------------------------------
  def transferFundFromLocked(from: Long, to: Long, currency: Currency, amount: Long) = {
    updateCashAccount(from, CashAccount(currency, 0, -amount, 0))
    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  }

  def transferFundFromAvailable(from: Long, to: Long, currency: Currency, amount: Long) = {
    updateCashAccount(from, CashAccount(currency, -amount, 0, 0))
    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  }

  def transferFundFromPendingWithdrawal(from: Long, to: Long, currency: Currency, amount: Long) = {
    updateCashAccount(from, CashAccount(currency, 0, 0, -amount))
    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  }

  def conditionalRefund(condition: Boolean)(currency: Currency, order: Order) = {
    if (condition && order.quantity > 0) refund(order.userId, currency, order.quantity)
  }

  def refund(uid: Long, currency: Currency, quantity: Long) = {
    updateCashAccount(uid, CashAccount(currency, quantity, -quantity, 0))
  }

  def canUpdateCashAccount(userId: Long, adjustment: CashAccount) = {
    val current = getUserCashAccount(userId, adjustment.currency)
    (current + adjustment).isValid
  }

  def updateCashAccount(userId: Long, adjustment: CashAccount) = {
    val current = getUserCashAccount(userId, adjustment.currency)
    val updated = current + adjustment
    assert(updated.isValid)
    setUserCashAccount(userId, updated)
  }

  def getUserAccounts(userId: Long): UserAccount =
    accountMap.get(userId).getOrElse(UserAccount(userId))

  private def getUserCashAccount(userId: Long, currency: Currency): CashAccount =
    getUserAccounts(userId).cashAccounts.getOrElse(currency, CashAccount(currency, 0, 0, 0))

  private def setUserCashAccount(userId: Long, cashAccount: CashAccount) = {
    if (!cashAccount.isValid)
      throw new IllegalArgumentException("Attempted to set user cash account to an invalid value: " + cashAccount)

    val accounts = accountMap.getOrElseUpdate(userId, UserAccount(userId))
    val updated = accounts.copy(cashAccounts = accounts.cashAccounts + (cashAccount.currency -> cashAccount))
    accountMap += userId -> updated
  }
}
