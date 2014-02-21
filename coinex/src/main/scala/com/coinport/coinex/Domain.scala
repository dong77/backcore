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
  case class DebugDump 
  case class DebugResetState 

  case class DoDeposit(deposit: Deposit) 
  case class DoWithdrawal(withdrawal: Withdrawal) 

  case class SubmitOrder(order: AnyRef) 
  case class CancelOrder(order: AnyRef) 

  //------------command responses
  case class DoDepositResult(deposit: Deposit)
  case class DoWithdrawalResult(withdrawal: Withdrawal)
  case class SubmitOrderResult(order: AnyRef)
  case class CancelOrderResult(order: AnyRef)
}