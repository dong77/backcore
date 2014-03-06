/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.domain

// Non-persistent messages
case class AccountOperationOK
case class AccountOperationFailed(error: AccountOperationError)

// Persistent Commands - from outside of the entire Akka system.
case class SubmitOrder(order: Order)
case class CancelOrder(id: Long)

sealed trait AccountOperationError
case object InsuffcientFund extends AccountOperationError
case object InvalidAmount extends AccountOperationError

case class DepositCash(userId: Long, currency: Currency, amount: BigDecimal)
case class RequestCashWithdrawal(userId: Long, currency: Currency, amount: BigDecimal)
case class ConfirmCashWithdrawalSuccess(userId: Long, currency: Currency, amount: BigDecimal)
case class ConfirmCashWithdrawalFailed(userId: Long, currency: Currency, amount: BigDecimal)

// Persistent Events
case class OrderSubmitted(order: Order)

case class TransactionsCreated(txs: Seq[Transaction])
case class OrderCancelled(order: Order)
case class OrdersTriggered(orders: Seq[Order]) // from coditional orders

case class NewTxPrice(marketSide: MarketSide, price: Double)