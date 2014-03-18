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
import Implicits._
import AccountOperationCode._

class AccountManager extends StateManager[AccountState] {
  initWithDefaultState(AccountState())

  def sendCash(from: Long, to: Long, currency: Currency, amount: Long) = {
    updateCashAccount(from, CashAccount(currency, 0, -amount, 0))
    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  }

  def conditionalRefund(condition: Boolean)(currency: Currency, order: Order) = {
    if (condition && order.quantity > 0) {
      updateCashAccount(order.userId, CashAccount(currency, order.quantity, -order.quantity, 0))
    }
  }

  def updateCashAccount(userId: Long, adjustment: CashAccount) = {
    val current = state.getUserCashAccount(userId, adjustment.currency)
    val updated = current + adjustment

    if (updated.isValid) {
      state = state.setUserCashAccount(userId, updated)
      AccountOperationResult(Ok, updated)
    } else {
      AccountOperationResult(InsufficientFund, current)
    }
  }

  def getAndIncreaseOrderId(): Long = {
    state = state.increaselLastOrderId()
    state.lastOrderId
  }
}