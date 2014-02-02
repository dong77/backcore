package com.coinport.exchange

// Commands ----
sealed trait Command
case class DoSubmitOrder(order: Order) extends Command
case class DoCancelOrder(orderId: Long) extends Command

case class DoDeposit(deposit: Deposit) extends Command
case class DoWithdraw(withdrawl: Withdrawl) extends Command

object SubmitOrderFailedReason extends Enumeration {
  type SubmitOrderFailedReason = Value
  val DuplicateOrder, InsufficientFund, InvalidMarket = Value
}

object CancelOrderFailedReason extends Enumeration {
  type CancelOrderFailedReason = Value
  val OrderExecuted, InvalidMarket = Value
}

import SubmitOrderFailedReason._
import CancelOrderFailedReason._

sealed trait Event
// Events ----
case class OrderCreationFailed(order: Order, reason: SubmitOrderFailedReason) extends Event
case class OrderCreated(order: Order) extends Event
case class OrderCancellationFailed(order: Order, reason: CancelOrderFailedReason) extends Event
case class OrderCancelled(order: Order) extends Event

case class DepositConfirmed(deposit: Deposit) extends Event
case class WithdrawlConfirmed(withdrawl: Withdrawl) extends Event
