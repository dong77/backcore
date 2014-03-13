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

package com.coinport.coinex.markets

import com.coinport.coinex.data._
import com.coinport.coinex.common.StateManager
import Implicits._
import OrderStatus._

class MarketManager(headSide: MarketSide) extends StateManager[MarketState] {
  initWithDefaultState(MarketState(headSide))
  private var collectTxs = true

  //This is for testing only
  private[markets] def disableCollectingTransactions() = collectTxs = false

  def addOrder(takerSide: MarketSide, takerOrder: Order): MarketUpdate = {
    val makerSide = takerSide.reverse

    def takerMpos = state.marketPriceOrderPool(takerSide)
    def takerLpos = state.limitPriceOrderPool(takerSide)
    def makerMpos = state.marketPriceOrderPool(makerSide)
    def makerLpos = state.limitPriceOrderPool(makerSide)

    // If the top order on the taker side is a market price order, it means
    // the maker side has no limit-price-order to match at all, we stop.
    var (remainingTakerQuantity, continue) = (takerOrder.quantity, takerMpos.isEmpty)
    var txs = List.empty[Transaction]
    var fullyExecutedOrders = List.empty[Order]
    var partiallyExecutedOrders = List.empty[Order]

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

        state = state.removeOrder(makerSide, makerOrder.id)
        fullyExecutedOrders ::= makerOrder
        if (collectTxs) {
          txs ::= Transaction(
            Transfer(takerOrder.userId, takerOrder.id, takerSide.outCurrency, txAmount, remainingTakerQuantity == 0),
            Transfer(makerOrder.userId, makerOrder.id, makerSide.outCurrency, makerOrder.quantity, true))

        }

      } else {
        // new taker order is fully executed but maker order is not.
        val txAmount = calcTxAmount(remainingTakerQuantity, true)
        val updatedMakerOrder = makerOrder.copy(quantity = makerOrder.quantity - txAmount)
        state = state.addOrder(makerSide, updatedMakerOrder)
        partiallyExecutedOrders ::= updatedMakerOrder
        if (collectTxs) {
          txs ::= Transaction(
            Transfer(takerOrder.userId, takerOrder.id, takerSide.outCurrency, remainingTakerQuantity, true),
            Transfer(makerOrder.userId, makerOrder.id, makerSide.outCurrency, txAmount, false))
        }
        remainingTakerQuantity = 0
      }
    }

    while (continue && remainingTakerQuantity > 0) {
      makerMpos.headOption match {
        // new LPO to match existing MPOs
        case Some(makerOrder) if takerOrder.vprice > 0 =>
          foundMatching(makerOrder, Left(takerOrder.vprice))

        case _ =>
          makerLpos.headOption match {
            // new LPO or MPO to match existing LPOs
            case Some(makerOrder) if makerOrder.vprice * takerOrder.vprice <= 1 =>
              foundMatching(makerOrder, Right(makerOrder.vprice))

            case _ =>
              continue = false
          }
      }
    }

    if (remainingTakerQuantity > 0) {
      state = state.addOrder(takerSide, takerOrder.copy(quantity = remainingTakerQuantity))
    }

    val status =
      if (remainingTakerQuantity == takerOrder.quantity) OrderStatus.Pending
      else if (remainingTakerQuantity > 0) OrderStatus.PartiallyExecuted
      else OrderStatus.FullyExecuted

    val orderInfo = OrderInfo(takerSide, takerOrder, status)

    MarketUpdate(orderInfo, remainingTakerQuantity, fullyExecutedOrders, partiallyExecutedOrders, txs)
  }

  def removeOrder(side: MarketSide, id: Long): Option[Order] = {
    val order = state.getOrder(side, id)
    order foreach { _ => state = state.removeOrder(side, id) }
    order
  }
}