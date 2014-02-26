package com.coinport.coinex.domain

/**
 * ATTENTION PLEASE:
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that we snapshot is taken and
 * persistent, the program can still update the live state.
 *
 * Transfer denotes the action to transfer `quantity` of `currency` FROM the account
 * with order id `orderId` to account with order id equals the `orderId` in the counterpart
 */

case class Transfer(orderId: Long, currency: Currency, quantity: Double, fullyExecuted: Boolean)

case class Transaction(taker: Transfer, maker: Transfer) {
  lazy val takerPrice = maker.quantity / taker.quantity
  lazy val makerPrice = taker.quantity / maker.quantity
}
  
   