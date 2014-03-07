/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.domain
case class Transfer(orderId: Long, currency: Currency, quantity: Double, fullyExecuted: Boolean)

case class Transaction(taker: Transfer, maker: Transfer) {
  lazy val takerPrice = maker.quantity / taker.quantity
  lazy val makerPrice = taker.quantity / maker.quantity
}
