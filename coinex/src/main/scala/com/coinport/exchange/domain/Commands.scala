package com.coinport.exchange.domain

case class Transfer(id: Long, uid: Long, amount: Double, status: String = "pending")

object Commands {
  sealed trait Command

  // Deposit -----------
  case class DoCreatePendingDeposit(t: Transfer) extends Command
  case class DoCancelDeposit(id: Long) extends Command
  case class DoConfirmDeposit(id: Long) extends Command
  case class DoFailDeposit(id: Long) extends Command

  // Withdrawal --------
  case class DoCreateWithdrawal(t: Transfer) extends Command
  case class DoCancelWithdrawal(id: Long) extends Command
  case class DoConfirmWithdrawal(id: Long) extends Command
  case class DoFailWithdrawal(id: Long) extends Command

  // Orders ------------
  case class DoCreateOrder extends Command
  case class DoCancelOrder extends Command
}

object Queries {
  sealed trait Query
  case class QueryUserAccountBalances extends Query
  case class QueryUserOrders extends Query
  case class QueryUserTransactions extends Query
  case class QueryRecentOrders extends Query
  case class QueryRecentTransactions extends Query
  case class QueryMarketInfo extends Query
}

object Events {
  sealed trait Event

  // Deposit -----------
  case class PendingDepositCreated(t: Transfer) extends Event
  case class DepositConcelled(t: Transfer) extends Event
  case class DepositConfirmed(t: Transfer) extends Event
  case class DepositFailed(t: Transfer, reason: String) extends Event

  // Withdrawal --------
  case class WithdrawalRequestCreated(t: Transfer) extends Event
  case class WithdrawalCancelled(t: Transfer) extends Event
  case class WithdrawalConfirmed(t: Transfer) extends Event
  case class WithdrawalFailed(t: Transfer, reason: String) extends Event

  // Orders ------------
  case class OrderCreated extends Event
  case class OrderCancelled extends Event
  case class OrderCancellationFailed extends Event
  case class TransactionsCreated extends Event
}
