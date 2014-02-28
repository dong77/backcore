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

package com.coinport.coinex.domain

/**
 * available: the amount the user can spend or withdraw.
 * withheld: the amount that has been put on lock for pending orders.
 */
case class CashAccount(currency: Currency, available: Double, withheld: Double) {
  def balance = available + withheld
}

/**
 * withheld: the amount as side.inCurrency that's been put on lock to cover debt
 * debt: the amount the user owe to the exchange as side.outCurrency
 *
 * If the current exchange rate between side.inCurrency and side.outCurrency is R,
 * then `withheld` should be between [debt x R/(1-r1), debt x R/(1-r0)], given that initial margin ratio is r0
 * and maintaining margin ratio is r1.
 *
 * If `withheld` is less than [debt x R/(1-r1), (debt x R/(1-r1) - withheld) will be transfered from
 * cash.available to margin.withheld, if money is not enough, an margin-call is issued;
 * if `withheld` is greater than debt x R/(1-r0), (withheld - debt x R/(1-r0)) will be
 * credited to cash.available.
 */
case class MarginAccount(side: MarketSide, withheld: Double, debt: Double)

object UserAccounts {
  type CashAccounts = Map[Currency, CashAccount]
  val EmptyCashAccounts = Map.empty[Currency, CashAccount]

  type MarginAccounts = Map[MarketSide, MarginAccount]
  val EmptyMarginAccounts = Map.empty[MarketSide, MarginAccount]
}

case class UserAccounts(userId: Long,
  cashAccounts: UserAccounts.CashAccounts = UserAccounts.EmptyCashAccounts,
  marginAccounts: UserAccounts.MarginAccounts = UserAccounts.EmptyMarginAccounts)
