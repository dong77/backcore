/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.exchange

object Transaction {
  var index = -1L
  def newTransaction(inCurrencyTrans: Transfer, outCurrencyTrans: Transfer, price: Long,
    buyOrderId: Long, sellOrderId: Long) = {
    index += 1
    Transaction(index, inCurrencyTrans, outCurrencyTrans, price, buyOrderId, sellOrderId, System.currentTimeMillis)
  }
}

case class Transfer(outUid: Long, inUid: Long, currency: Currency, quantity: Long)

case class Transaction(id: Long, inCurrencyTrans: Transfer, outCurrencyTrans: Transfer, price: Long,
  buyOrderId: Long, sellOrderId: Long, timestamp: Long)
