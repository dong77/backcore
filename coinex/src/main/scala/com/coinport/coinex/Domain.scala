package com.coinport.coinex

object Domain {
  type UserId = Long
  type OrderId = Long
  type Currency = String
  type Amount = Double
  type TxId = Long

  //------------domain objects
  case class Market(in: Currency, out: Currency) {
    override def toString = out.toLowerCase() + "_" + in.toLowerCase()
  }

  case class Account(currency: Currency, spendable: Amount = 0.0, locked: Amount = 0.0)
  case class UserAccount(uid: UserId, accounts: Map[Currency, Account])

  case class Deposit(uid: UserId, currency: String, amount: Amount)
  case class Withdrawal(uid: UserId, currency: String, amount: Amount)

  case class BuyOrder(id: OrderId, uid: UserId, market: Market, payAmount: Double, price: Option[Amount]) { def claimAmount = price.map(p => payAmount / p) }
  case class SellOrder(id: OrderId, uid: UserId, market: Market, payAmount: Double, price: Option[Amount]) { def claimAmount = price.map(p => payAmount * p) }

  case class Transfer(currency: Currency, from: UserId, to: UserId, amount: Amount)
  case class SellTx(id: TxId, market: Market, price: Amount, amountSold: Amount, orderId: OrderId, partial: Boolean) { def inAmount = amountSold * price }
  case class BuyTx(id: TxId, market: Market, price: Amount, amountBought: Amount, orderId: OrderId, partial: Boolean) { def outAmount = amountBought * price }

  //------------commands
  sealed trait Cmd
  case class DebugDump extends Cmd
  case class DebugResetState extends Cmd

  case class DoDeposit(deposit: Deposit) extends Cmd
  case class DoWithdrawal(withdrawal: Withdrawal) extends Cmd

  case class SubmitOrder(order: AnyRef) extends Cmd
  case class CancelOrder(order: AnyRef) extends Cmd

  //------------command responses
  case class DoDepositResult(deposit: Deposit)
  case class DoWithdrawalResult(withdrawal: Withdrawal)
  case class SubmitOrderResult(order: AnyRef)
  case class CancelOrderResult(order: AnyRef)

  //------------events
  sealed trait Evt
  case class DepositConfirmed(deposit: Deposit) extends Evt
  case class WithdrawalConfirmed(withdrawal: Withdrawal) extends Evt

  case class OrderSubmitted(order: AnyRef) extends Evt
  case class OrderCancelled(order: AnyRef) extends Evt
  case class OrderCancellationFailed(order: AnyRef, reason: String) extends Evt
  case class TxConfirmed(sellTx: SellTx, buyTx: BuyTx, sellTransfer: Transfer, buyTransfer: Transfer) extends Evt
}