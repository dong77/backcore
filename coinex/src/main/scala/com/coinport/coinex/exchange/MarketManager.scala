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

package com.coinport.coinex.exchange

import com.coinport.coinex.domain.StateManager

import BuyOrSell._
import MarketOrLimit._

object MarketManager {
  var matchCondition = Map.empty[BuyOrSell, (OrderData, OrderData) => Boolean]
  matchCondition += (BUY -> ((taker: OrderData, maker: OrderData) => taker.price >= maker.price))
  matchCondition += (SELL -> ((taker: OrderData, maker: OrderData) => taker.price <= maker.price))
}

class MarketManager(buySide: MarketSide) extends StateManager[MarketState] {
  initWithDefaultState(MarketState(buySide))

  // TODO(c) support market order
  def addOrder(order: Order): List[Transaction] = {
    checkOrder(order)

    val takerOrder = order.data
    val marketOrLimit = takerOrder.marketOrLimit
    val (takerBuyOrSell, makerBuyOrSell) =
      (takerOrder.buyOrSell, BuyOrSell.reverse(takerOrder.buyOrSell))

    def makerLpos = state.getOrderPool(marketOrLimit, makerBuyOrSell)
    var (remainingTakerQuantity, continue) = (takerOrder.quantity, true)

    var txs = List.empty[Transaction]
    val checker = MarketManager.matchCondition.get(takerBuyOrSell).get

    while (remainingTakerQuantity > 0 && continue) {
      makerLpos.headOption match {
        case Some(makerOrder) =>
          if (!checker(takerOrder, makerOrder)) {
            continue = false
            if (remainingTakerQuantity > 0)
              state = state.addOrder(order.copy(data = takerOrder.copy(quantity = remainingTakerQuantity)))
          } else {
            val (buyOrder, sellOrder) = (if (takerBuyOrSell == BUY) (takerOrder, makerOrder) else (makerOrder, takerOrder))
            var remainingMakerQuantity = makerOrder.quantity
            val tradeQuantity = java.lang.Math.min(remainingTakerQuantity, remainingMakerQuantity)
            remainingTakerQuantity -= tradeQuantity
            remainingMakerQuantity -= tradeQuantity

            val tradeAmount = tradeQuantity * makerOrder.price

            txs ::= Transaction.newTransaction(
              Transfer(sellOrder.uid, buyOrder.uid, order.side.inCurrency, tradeQuantity),
              Transfer(buyOrder.uid, sellOrder.uid, order.side.outCurrency, tradeAmount),
              makerOrder.price, buyOrder.id, sellOrder.id)
            if (remainingMakerQuantity == 0) {
              state = state.removeOrder(makerOrder.id)
            } else {
              state = state.addOrder(Order(order.side, makerOrder.copy(quantity = remainingMakerQuantity)))
            }
          }
        case _ =>
          if (remainingTakerQuantity > 0)
            state = state.addOrder(order.copy(data = takerOrder.copy(quantity = remainingTakerQuantity)))
          continue = false
      }
    }
    txs
  }

  def removeOrder(id: Long): Option[Order] = {
    // TODO
    None
  }
  def checkOrder(order: Order) {
    assert(state.marketSide == order.side)
  }
}
