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

  def addOrder(sellSide: MarketSide, sellOrder: Order): MarketUpdate = {
    val buySide = sellSide.reverse

    def sellMpos = state.marketPriceOrderPool(sellSide)
    def sellLpos = state.limitPriceOrderPool(sellSide)
    def buyMpos = state.marketPriceOrderPool(buySide)
    def buyLpos = state.limitPriceOrderPool(buySide)

    // If the top order on the sell side is a market price order, it means
    // the buy side has no limit-price-order to match at all, we stop.
    var sellOrderRemainingQuantity = sellOrder.quantity
    var sellOrderRemainingTakeLimit = sellOrder.takeLimit
    var continue = sellMpos.isEmpty

    var txs = List.empty[Transaction]
    var fullyExecutedOrders = List.empty[Order]
    var partiallyExecutedOrders = List.empty[Order]
    var unlockFunds = List.empty[UnlockFund]

    // We extract common logics into an inner method for better readability.
    def foundMatching(buyOrder: Order, price: Double) {

      // Calculate the amount buyOrder can afford to buy, buyPower will be like 14000RMB
      val maxSellAmount = sellOrderRemainingTakeLimit match {
        case Some(limit) if limit / price < sellOrderRemainingQuantity => limit / price
        case _ => sellOrderRemainingQuantity
      }
      val maxBuyAmount = buyOrder.takeLimit match {
        case Some(limit) if limit < buyOrder.quantity / price => limit
        case _ => buyOrder.quantity / price
      }

      val outAmount = Math.min(maxSellAmount, maxBuyAmount).toLong
      val inAmount = (outAmount * price).toLong
      val buyOrderRemainingQuantity = buyOrder.quantity - inAmount
      val buyOrderRemainingTakeLimit = buyOrder.takeLimit map (_ - outAmount)
      val updatedBuyOrder = buyOrder.copy(quantity = buyOrderRemainingQuantity, takeLimit = buyOrderRemainingTakeLimit)
      state = state.removeOrder(buySide, buyOrder.id)

      sellOrderRemainingQuantity -= outAmount
      sellOrderRemainingTakeLimit = sellOrderRemainingTakeLimit map (_ - inAmount)

      if (collectTxs) {
        txs ::= Transaction(
          Transfer(sellOrder.userId, sellOrder.id, sellSide.outCurrency, outAmount, sellOrderRemainingQuantity == 0),
          Transfer(buyOrder.userId, buyOrder.id, buySide.outCurrency, inAmount, buyOrderRemainingQuantity == 0))
      }

      // Check if sell order is fully executed or take limit hit
      if (sellOrderRemainingQuantity == 0) {
        fullyExecutedOrders ::= buyOrder
        continue = false
      } else if (sellOrderRemainingTakeLimit == Some(0)) {
        partiallyExecutedOrders ::= buyOrder
        continue = false
      }

      // Check if buy order is fully executed or take limit hit
      if (buyOrderRemainingQuantity == 0) {
        fullyExecutedOrders ::= updatedBuyOrder
      } else if (buyOrderRemainingTakeLimit == Some(0)) {
        partiallyExecutedOrders ::= updatedBuyOrder
      } else {
        partiallyExecutedOrders ::= updatedBuyOrder
        state = state.addOrder(buySide, updatedBuyOrder)
        continue = false
      }

      // handles refund
      if (sellOrderRemainingTakeLimit == Some(0) && buyOrderRemainingQuantity > 0) {
        unlockFunds ::= UnlockFund(sellOrder.userId, sellSide.outCurrency, sellOrderRemainingQuantity)
      }
      if (buyOrderRemainingTakeLimit == Some(0) && updatedBuyOrder.quantity > 0) {
        //Refund(newMakerOrder.quantity)
        unlockFunds ::= UnlockFund(buyOrder.userId, buySide.outCurrency, buyOrderRemainingQuantity)
      }
    }

    while (continue && sellOrderRemainingQuantity > 0) {
      buyMpos.headOption match {
        // new LPO to match existing MPOs
        case Some(buyOrder) if sellOrder.vprice > 0 => foundMatching(buyOrder, sellOrder.vprice)
        case _ =>
          buyLpos.headOption match {
            // new LPO or MPO to match existing LPOs
            case Some(buyOrder) if buyOrder.vprice * sellOrder.vprice <= 1 => foundMatching(buyOrder, 1 / buyOrder.vprice)
            case _ => continue = false
          }
      }
    }

    if (sellOrderRemainingQuantity > 0 && sellOrderRemainingTakeLimit != Some(0)) {
      state = state.addOrder(sellSide, sellOrder.copy(quantity = sellOrderRemainingQuantity))
    }

    val status =
      if (sellOrderRemainingQuantity == sellOrder.quantity) OrderStatus.Pending
      else if (sellOrderRemainingQuantity > 0) OrderStatus.PartiallyExecuted
      else OrderStatus.FullyExecuted

    val orderInfo = OrderInfo(sellSide, sellOrder, status)

    MarketUpdate(orderInfo, sellOrderRemainingQuantity, fullyExecutedOrders, partiallyExecutedOrders, txs, unlockFunds)
  }

  def removeOrder(side: MarketSide, id: Long): Option[Order] = {
    val order = state.getOrder(side, id)
    order foreach { _ => state = state.removeOrder(side, id) }
    order
  }
}