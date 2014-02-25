package com.coinport.coinex.domain

// Transfer denotes the action to transfer `amount` of `currency` FROM the account with order id `orderId`
// to account with order id equals the `orderId` in the counterpart
case class Transfer(orderId: Long, currency: Currency, amount: Double)

case class Transaction(left: Transfer, right: Transfer) {
  lazy val leftPrice = right.amount / left.amount
  lazy val rightPrice = left.amount / right.amount
}
  
   