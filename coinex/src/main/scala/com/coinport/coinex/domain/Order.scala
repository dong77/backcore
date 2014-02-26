package com.coinport.coinex.domain

/**
 * ATTENTION PLEASE:
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that we snapshot is taken and
 * persistent, the program can still update the live state.
 *
 */

case class OrderData(id: Long, quantity: Double, price: Double = 0)

case class Order(side: MarketSide, data: OrderData)

