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

    private def getRemaining(order: OrderData, another: OrderData, price: Long) = {
        (order.marketOrLimit, order.buyOrSell) match {
            case (MARKET, BUY) =>
                order.copy(quantity = order.amount / price)
            case (_, _) =>
                order
        }
    }

    private def trade(order: OrderData, tradeQuantity: Long, price: Long) = {
        (order.marketOrLimit, order.buyOrSell) match {
            case (MARKET, BUY) =>
                order.copy(quantity = order.quantity - tradeQuantity, amount = order.amount - tradeQuantity * price)
            case (_, _) =>
                order.copy(quantity = order.quantity - tradeQuantity)
        }
    }

    def addOrder(order: Order): List[Transaction] = {
        checkOrder(order)

        var remainingTakerOrder = modifyIfNeed(order.data)
        val (takerBuyOrSell, makerBuyOrSell) =
            (remainingTakerOrder.buyOrSell, BuyOrSell.reverse(remainingTakerOrder.buyOrSell))

        def makerLpos = state.getOrderPool(LIMIT, makerBuyOrSell)
        def makerMpos = state.getOrderPool(MARKET, makerBuyOrSell)
        var continue = true

        var txs = List.empty[Transaction]
        val checker = MarketManager.matchCondition.get(takerBuyOrSell).get

        def matching(makerOrder: OrderData, actualPrice: Long) {
            var remainingMakerOrder = makerOrder
            if (!checker(remainingTakerOrder, remainingMakerOrder)) {
                continue = false
                if (remainingTakerOrder.quantity > 0)
                    state = state.addOrder(order.copy(data = remainingTakerOrder))
            } else {
                remainingTakerOrder = getRemaining(remainingTakerOrder, remainingMakerOrder, actualPrice)
                remainingMakerOrder = getRemaining(remainingMakerOrder, remainingTakerOrder, actualPrice)
                val tradeQuantity = java.lang.Math.min(remainingTakerOrder.quantity, remainingMakerOrder.quantity)

                remainingTakerOrder = trade(remainingTakerOrder, tradeQuantity, actualPrice)
                remainingMakerOrder = trade(remainingMakerOrder, tradeQuantity, actualPrice)

                val (buyOrder, sellOrder) = (if (takerBuyOrSell == BUY)
                    (remainingTakerOrder, remainingMakerOrder) else (remainingMakerOrder, remainingTakerOrder))

                val tradeAmount = tradeQuantity * actualPrice

                txs ::= Transaction.newTransaction(
                    Transfer(sellOrder.uid, buyOrder.uid, order.side.inCurrency, tradeQuantity),
                    Transfer(buyOrder.uid, sellOrder.uid, order.side.outCurrency, tradeAmount),
                    actualPrice, buyOrder.id, sellOrder.id)
                if (remainingMakerOrder.quantity > 0) {
                    state = state.addOrder(Order(order.side, remainingMakerOrder))
                } else {
                    state = state.removeOrder(remainingMakerOrder.id)
                }
            }
        }

        while (remainingTakerOrder.quantity > 0 && continue) {
            makerLpos.headOption match {
                case Some(makerOrder) => matching(makerOrder, makerOrder.price)
                case _ =>
                    // can't exchange between market buy order and market sell order
                    if (remainingTakerOrder.marketOrLimit == MARKET) {
                        if (remainingTakerOrder.quantity > 0)
                            state = state.addOrder(order.copy(data = remainingTakerOrder))
                        continue = false
                    } else {
                        makerMpos.headOption match {
                            case Some(makerOrder) => matching(makerOrder, remainingTakerOrder.price)
                            case _ =>
                            if (remainingTakerOrder.quantity > 0)
                                state = state.addOrder(order.copy(data = remainingTakerOrder))
                            continue = false
                        }
                    }
            }
        }
        txs
    }

    def removeOrder(id: Long): Option[Order] = {
        // TODO
        None
    }

    private def checkOrder(order: Order) {
        assert(state.marketSide == order.side)
    }

    private def modifyIfNeed(orderData: OrderData) = {
        if (orderData.marketOrLimit == MARKET) {
            if (orderData.buyOrSell == BUY) {
                orderData.copy(price = Long.MaxValue, quantity = Long.MaxValue)
            } else {
                orderData.copy(price = Long.MinValue)
            }
        } else {
            orderData
        }
    }
}
