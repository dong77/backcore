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
    var remainingTakerQuantity = takerOrder.quantity
    var continue = takerMpos.isEmpty
    var remainingTakeLimit = takerOrder.takeLimit

    var txs = List.empty[Transaction]
    var fullyExecutedOrders = List.empty[Order]
    var partiallyExecutedOrders = List.empty[Order]

    // We extract common logics into an inner method for better readability.
    def foundMatching(makerOrder: Order, actualTakerPrice: Double) {

      // Calculate the amount makerOrder can afford to buy, buyPower will be like 14000RMB

      val maxSellAmount = remainingTakeLimit match {
        case Some(limit) if limit / actualTakerPrice < remainingTakerQuantity => limit / actualTakerPrice
        case _ => remainingTakerQuantity
      }
      val maxBuyAmount = makerOrder.takeLimit match {
        case Some(limit) if limit < makerOrder.quantity / actualTakerPrice => limit
        case _ => makerOrder.quantity / actualTakerPrice
      }

      val txAmount = Math.min(maxSellAmount, maxBuyAmount).toLong
      val txAmountReverse = (txAmount * actualTakerPrice).toLong

      remainingTakerQuantity -= txAmount

      remainingTakeLimit = remainingTakeLimit match {
        case Some(limit) => Some(limit - txAmountReverse)
        case None => None
      }

      val makerOrderRemaining = makerOrder.quantity - txAmountReverse
      val makerOrderRemainingTakeLimit = makerOrder.takeLimit map (_ - txAmount)
      val newMakerOrder = makerOrder.copy(quantity = makerOrderRemaining, takeLimit = makerOrderRemainingTakeLimit)

      if (collectTxs) {
        txs ::= Transaction(
          Transfer(takerOrder.userId, takerOrder.id, takerSide.outCurrency, txAmount, remainingTakerQuantity == 0),
          Transfer(makerOrder.userId, makerOrder.id, makerSide.outCurrency, txAmountReverse, newMakerOrder.quantity == 0))

      }

      // Check if sell order is fully executed
      if (remainingTakerQuantity == 0 || remainingTakeLimit == Some(0)) {
        fullyExecutedOrders ::= takerOrder
        continue = false
      }

      // check if buy oder is fully executed

      state = state.removeOrder(makerSide, makerOrder.id)
      if (makerOrderRemaining == 0 || makerOrderRemainingTakeLimit == Some(0)) {
        fullyExecutedOrders ::= newMakerOrder
      } else {
        state = state.addOrder(makerSide, newMakerOrder)
        partiallyExecutedOrders ::= newMakerOrder
        continue = false
      }

      // handles refund

      if (remainingTakeLimit == Some(0) && makerOrderRemaining > 0) {
        //Refund(makerOrderRemaining)
      }
      if (makerOrderRemainingTakeLimit == Some(0) && newMakerOrder.quantity > 0) {
        //Refund(newMakerOrder.quantity)
      }

    }

    while (continue && remainingTakerQuantity > 0) {
      makerMpos.headOption match {
        // new LPO to match existing MPOs
        case Some(makerOrder) if takerOrder.vprice > 0 =>
          foundMatching(makerOrder, takerOrder.vprice)

        case _ =>
          makerLpos.headOption match {
            // new LPO or MPO to match existing LPOs
            case Some(makerOrder) if makerOrder.vprice * takerOrder.vprice <= 1 =>
              foundMatching(makerOrder, 1 / makerOrder.vprice)

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