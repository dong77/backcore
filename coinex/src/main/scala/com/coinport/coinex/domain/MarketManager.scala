/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * MarketManager is the maintainer of a Market. It executes new orders before
 * they are added into a market as pending orders. As execution results, a list
 * of Transactions are generated and returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 *
 * Note this class does NOT depend on event-sourcing framework we choose. Please
 * keep it plain old scala/java.
 */

package com.coinport.coinex.domain

class MarketManager(headSide: MarketSide) {

  private var market = Market(headSide)

  def apply() = market

  def reset(market: Option[Market] = None) = market match {
    case Some(m) => this.market = m
    case None => this.market = Market(headSide)
  }

  def addOrder(order: Order): List[Transaction] = {
    checkOrder(order)
    val (takerSide, makerSide) = (order.side, order.side.reverse)
    val takerOrder = order.data

    def takerMpos = market.marketPriceOrderPool(takerSide)
    def takerLpos = market.limitPriceOrderPool(takerSide)
    def makerMpos = market.marketPriceOrderPool(makerSide)
    def makerLpos = market.limitPriceOrderPool(makerSide)

    // If the top order on the taker side is a market price order, it means
    // the maker side has no limit-price-order to match at all, we stop.
    var (remainingTakerQuantity, continue) = (takerOrder.quantity, takerMpos.isEmpty)
    var txs = List.empty[Transaction]

    // We extract common logics into an inner method for better readability.
    def foundMatching(makerOrder: OrderData, actualTakerPrice: Double) {
      val actualMakerPrice = 1 / actualTakerPrice
      if (remainingTakerQuantity >= makerOrder.quantity * actualMakerPrice) {
        // new taker order is not fully executed but maker order is.
        remainingTakerQuantity -= makerOrder.quantity * actualMakerPrice
        market = market.removeOrder(makerOrder.id)
        txs ::= Transaction(
          Transfer(takerOrder.id, takerSide.outCurrency, makerOrder.quantity * actualMakerPrice, remainingTakerQuantity == 0),
          Transfer(makerOrder.id, makerSide.outCurrency, makerOrder.quantity, true))

      } else {
        // new taker order is fully executed but maker order is not.
        val updatedMakerOrder = Order(makerSide, makerOrder.copy(quantity = makerOrder.quantity - remainingTakerQuantity * actualTakerPrice))
        market = market.addOrder(updatedMakerOrder)
        txs ::= Transaction(
          Transfer(takerOrder.id, takerSide.outCurrency, remainingTakerQuantity, true),
          Transfer(makerOrder.id, makerSide.outCurrency, remainingTakerQuantity * actualTakerPrice, false))
        remainingTakerQuantity = 0
      }
    }

    while (continue && remainingTakerQuantity > 0) {
      makerMpos.headOption match {
        // new LPO to match existing MPOs
        case Some(makerOrder) if takerOrder.price > 0 =>
          foundMatching(makerOrder, actualTakerPrice = takerOrder.price)

        case _ =>
          makerLpos.headOption match {
            // new LPO or MPO to match existing LPOs
            case Some(makerOrder) if makerOrder.price * takerOrder.price <= 1 =>
              foundMatching(makerOrder, actualTakerPrice = 1 / makerOrder.price)

            case _ =>
              continue = false
          }
      }
    }

    // The new order is not fully executed, so we add a pending order into the pool
    if (remainingTakerQuantity > 0) {
      market = market.addOrder(order.copy(data = takerOrder.copy(quantity = remainingTakerQuantity)))
    }

    txs
  }

  def checkOrder(order: Order) {
    assert(market.bothSides.contains(order.side))
  }
}