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

class MarketManager(headSide: MarketSide) extends StateManager[MarketState] {
  initWithDefaultState(MarketState(headSide))

  private val bigDecimalScale = 8
  private val bigDecimalRoundingMode = scala.math.BigDecimal.RoundingMode.HALF_EVEN

  private def scale(v: BigDecimal) = v.setScale(bigDecimalScale, bigDecimalRoundingMode)

  def addOrder(order: Order): List[Transaction] = {
    checkOrder(order)
    val (takerSide, makerSide) = (order.side, order.side.reverse)
    val takerOrder = order.data

    def takerMpos = state.marketPriceOrderPool(takerSide)
    def takerLpos = state.limitPriceOrderPool(takerSide)
    def makerMpos = state.marketPriceOrderPool(makerSide)
    def makerLpos = state.limitPriceOrderPool(makerSide)

    // If the top order on the taker side is a market price order, it means
    // the maker side has no limit-price-order to match at all, we stop.
    var (remainingTakerQuantity, continue) = (takerOrder.quantity, takerMpos.isEmpty)
    var txs = List.empty[Transaction]

    // We extract common logics into an inner method for better readability.
    def foundMatching(makerOrder: OrderData, actualTakerPrice: BigDecimal) {
      val actualMakerPrice = 1 / actualTakerPrice
      if (remainingTakerQuantity >= makerOrder.quantity * actualMakerPrice) {
        // new taker order is not fully executed but maker order is.
        remainingTakerQuantity = scale(remainingTakerQuantity - makerOrder.quantity * actualMakerPrice)

        state = state.removeOrder(makerOrder.id)
        txs ::= Transaction(
          Transfer(takerOrder.id, takerSide.outCurrency, scale(makerOrder.quantity * actualMakerPrice), remainingTakerQuantity == 0),
          Transfer(makerOrder.id, makerSide.outCurrency, makerOrder.quantity, true))

      } else {
        // new taker order is fully executed but maker order is not.
        val updatedMakerOrder = Order(makerSide, makerOrder.copy(quantity = scale(makerOrder.quantity - remainingTakerQuantity * actualTakerPrice)))
        state = state.addOrder(updatedMakerOrder)
        txs ::= Transaction(
          Transfer(takerOrder.id, takerSide.outCurrency, remainingTakerQuantity, true),
          Transfer(makerOrder.id, makerSide.outCurrency, scale(remainingTakerQuantity * actualTakerPrice), false))
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
      state = state.addOrder(order.copy(data = takerOrder.copy(quantity = remainingTakerQuantity)))
    }

    txs
  }

  def removeOrder(id: Long): Option[Order] = {
    // TODO
    None
  }
  def checkOrder(order: Order) {
    assert(state.bothSides.contains(order.side))
  }
}