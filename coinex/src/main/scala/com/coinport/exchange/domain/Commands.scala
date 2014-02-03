package com.coinport.exchange.domain

object Commands {
  sealed trait Command

  // Deposit -----------
  case class DoCreatePendingVirtualDeposit(deposit: Deposit) extends Command
  case class DoCreatePendingFaitDeposit(deposit: Deposit) extends Command
  case class DoConfirmFaitDeposit(depositId: Long) extends Command
  case class DoFailFaitDeposit(depositId: Long) extends Command
  //case class DoCreateAdminDeposit extends Command

  // Withdrawal --------
  case class DoCreateWithdrawal(withdrawal: Withdrawal) extends Command
  case class DoCancelWithdrawal(withdrawalId: Long) extends Command
  case class DoConfirmWithdrawal(withdrawalId: Long) extends Command
  case class DoFailWithdrawal(withdrawalId: Long) extends Command

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
  case class PendingVirtualDepositCreated(deposit: Deposit) extends Event
  case class PendingFaitDepositCreated(deposit: Deposit) extends Event
  case class AdminDepositCreated(deposit: Deposit) extends Event
  case class DepositConfirmed(deposit: Deposit) extends Event
  case class DepositFailed(deposit: Deposit) extends Event

  // Withdrawal --------
  case class WithdrawalRequestCreated(withdrawal: Withdrawal) extends Event
  case class WithdrawalCancelled(withdrawal: Withdrawal) extends Event
  case class WithdrawalConfirmed(withdrawal: Withdrawal) extends Event
  case class WithdrawalFailed(withdrawal: Withdrawal) extends Event

  // Orders ------------
  case class OrderCreated extends Event
  case class OrderCancelled extends Event
  case class OrderCancellationFailed extends Event
  case class TransactionsCreated extends Event
}
