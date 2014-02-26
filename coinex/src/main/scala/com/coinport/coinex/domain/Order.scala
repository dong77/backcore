package com.coinport.coinex.domain

/**
 * ATTENTION PLEASE:
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that we snapshot is taken and
 * persistent, the program can still update the live state.
 *
 */

case class Trailing(highestTxPrice: Double, param: Either[ /*percentage*/ Double, /*absolute*/ Double])

case class OrderData(id: Long, quantity: Double, price: Double = 0, trailing: Option[Trailing] = None)

case class Order(side: MarketSide, data: OrderData)

case class StopOrder(stopPrice: Double, order: Order)