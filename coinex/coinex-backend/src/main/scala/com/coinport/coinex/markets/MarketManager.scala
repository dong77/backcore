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

  def addOrder(sellSide: MarketSide, sellOrder: Order): MarketUpdate = {
    val buySide = sellSide.reverse

    def sellMpos = state.marketPriceOrderPool(sellSide)
    def sellLpos = state.limitPriceOrderPool(sellSide)
    def buyMpos = state.marketPriceOrderPool(buySide)
    def buyLpos = state.limitPriceOrderPool(buySide)

    // If the top order on the sell side is a market price order, it means
    // the buy side has no limit-price-order to match at all, we stop.
    var remainingQuantity = sellOrder.quantity
    var remainingTakeLimit = sellOrder.takeLimit
    var totalInAmount = 0L
    var continue = sellMpos.isEmpty

    var txs = List.empty[Transaction]
    var matchedOrders = List.empty[OrderInfo]
    var unlockCashs = List.empty[UnlockFund]
    var firstPrice: Option[Double] = None
    var lastPrice: Option[Double] = None

    // We extract common logics into an inner method for better readability.
    def foundMatching(buyOrder: Order, price: Double) {
      lastPrice = Some(price)
      if (firstPrice.isEmpty) firstPrice = lastPrice

      // Calculate the amount buyOrder can afford to buy, buyPower will be like 14000RMB
      val maxSellAmount = remainingTakeLimit match {
        case Some(limit) if limit / price < remainingQuantity => limit / price
        case _ => remainingQuantity
      }
      val maxBuyAmount = buyOrder.takeLimit match {
        case Some(limit) if limit < buyOrder.quantity / price => limit
        case _ => buyOrder.quantity / price
      }

      val outAmount = Math.min(maxSellAmount, maxBuyAmount).toLong
      val inAmount = (outAmount * price).toLong
      remainingQuantity -= outAmount
      totalInAmount += inAmount
      remainingTakeLimit = remainingTakeLimit map (_ - inAmount)

      val buyOrderRemainingAmount = buyOrder.quantity - inAmount
      val buyOrderRemainingTakeLimit = buyOrder.takeLimit map (_ - outAmount)
      val buyOrderStatus = if (buyOrderRemainingAmount == 0) OrderStatus.FullyExecuted else OrderStatus.PartiallyExecuted

      val updatedBuyOrder = buyOrder.copy(quantity = buyOrderRemainingAmount, takeLimit = buyOrderRemainingTakeLimit)
      state = state.removeOrder(buySide, buyOrder.id)

      val buyOrderInfo = OrderInfo(buySide, buyOrder, buyOrderStatus, buyOrderRemainingAmount, outAmount)
      matchedOrders ::= buyOrderInfo

      txs ::= Transaction(
        Transfer(sellOrder.userId, sellOrder.id, sellSide.outCurrency, outAmount, remainingQuantity == 0),
        Transfer(buyOrder.userId, buyOrder.id, buySide.outCurrency, inAmount, buyOrderRemainingAmount == 0))

      // Check if sell order is fully executed or take limit hit
      if (remainingQuantity == 0 || remainingTakeLimit == Some(0)) {
        continue = false
      }

      // Check if buy order is fully executed or take limit hit
      if (!(buyOrderRemainingAmount == 0 || buyOrderRemainingTakeLimit == Some(0))) {
        state = state.addOrder(buySide, updatedBuyOrder)
        continue = false
      }

      // handles refund
      if (remainingTakeLimit == Some(0) && buyOrderRemainingAmount > 0) {
        unlockCashs ::= UnlockFund(sellOrder.userId, sellSide.outCurrency, remainingQuantity)
      }
      if (buyOrderRemainingTakeLimit == Some(0) && updatedBuyOrder.quantity > 0) {
        unlockCashs ::= UnlockFund(buyOrder.userId, buySide.outCurrency, buyOrderRemainingAmount)
      }
    }

    while (continue && remainingQuantity > 0) {
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

    if (remainingQuantity > 0 && remainingTakeLimit != Some(0)) {
      state = state.addOrder(sellSide, sellOrder.copy(quantity = remainingQuantity))
    }

    val status =
      if (remainingQuantity == sellOrder.quantity) OrderStatus.Pending
      else if (remainingQuantity > 0) OrderStatus.PartiallyExecuted
      else OrderStatus.FullyExecuted

    val orderInfo = OrderInfo(sellSide, sellOrder, status, remainingQuantity, totalInAmount)

    MarketUpdate(orderInfo,
      sellOrder.quantity - remainingQuantity,
      totalInAmount,
      matchedOrders,
      txs,
      unlockCashs,
      firstPrice,
      lastPrice)
  }

  def removeOrder(side: MarketSide, id: Long): Option[Order] = {
    val order = state.getOrder(side, id)
    order foreach { _ => state = state.removeOrder(side, id) }
    order
  }
}