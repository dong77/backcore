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

import MarketState.priceOf

class MarketManager(headSide: MarketSide) extends StateManager[MarketState] {
  initWithDefaultState(MarketState(headSide))
  private var collectTxs = true

  //This is for testing only
  private[domain] def disableCollectingTransactions() = this.collectTxs = false

  def addOrder(takerSide: MarketSide, takerOrder: Order): List[Transaction] = {
    val makerSide = takerSide.reverse

    def takerMpos = state.marketPriceOrderPool(takerSide)
    def takerLpos = state.limitPriceOrderPool(takerSide)
    def makerMpos = state.marketPriceOrderPool(makerSide)
    def makerLpos = state.limitPriceOrderPool(makerSide)

    // If the top order on the taker side is a market price order, it means
    // the maker side has no limit-price-order to match at all, we stop.
    var (remainingTakerQuantity, continue) = (takerOrder.quantity, takerMpos.isEmpty)
    var txs = List.empty[Transaction]

    // We extract common logics into an inner method for better readability.
    def foundMatching(makerOrder: Order, actualTakerPrice: Either[Double /*multiply*/ , Double /*divide*/ ]) {
      def calcTxAmount(amount: Long, multiply: Boolean) = {
        val total = actualTakerPrice match {
          case Left(v) => if (multiply) amount * v else amount / v
          case Right(v) => if (multiply) amount / v else amount * v
        }
        total.toLong
      }

      val txAmount = calcTxAmount(makerOrder.quantity, false)
      if (remainingTakerQuantity >= txAmount) {
        // new taker order is not fully executed but maker order is.
        remainingTakerQuantity -= txAmount

        state = state.removeOrder(makerOrder.id)
        if (collectTxs) {
          txs ::= Transaction(
            Transfer(takerOrder.id, takerSide.outCurrency, txAmount, remainingTakerQuantity == 0),
            Transfer(makerOrder.id, makerSide.outCurrency, makerOrder.quantity, true))
        }

      } else {
        // new taker order is fully executed but maker order is not.
        val txAmount = calcTxAmount(remainingTakerQuantity, true)
        val updatedMakerOrder = makerOrder.copy(quantity = makerOrder.quantity - txAmount)
        state = state.addOrder(makerSide, updatedMakerOrder)
        if (collectTxs) {
          txs ::= Transaction(
            Transfer(takerOrder.id, takerSide.outCurrency, remainingTakerQuantity, true),
            Transfer(makerOrder.id, makerSide.outCurrency, txAmount, false))
        }
        remainingTakerQuantity = 0
      }
    }

    while (continue && remainingTakerQuantity > 0) {
      makerMpos.headOption match {
        // new LPO to match existing MPOs
        case Some(makerOrder) if priceOf(takerOrder) > 0 =>
          foundMatching(makerOrder, Left(priceOf(takerOrder)))

        case _ =>
          makerLpos.headOption match {
            // new LPO or MPO to match existing LPOs
            case Some(makerOrder) if priceOf(makerOrder) * priceOf(takerOrder) <= 1 =>
              foundMatching(makerOrder, Right(priceOf(makerOrder)))

            case _ =>
              continue = false
          }
      }
    }

    // The new order is not fully executed, so we add a pending order into the pool
    if (remainingTakerQuantity > 0) {
      state = state.addOrder(takerSide, takerOrder.copy(quantity = remainingTakerQuantity))
    }

    txs
  }

  def removeOrder(id: Long): Option[Order] = {
    // TODO
    None
  }
}