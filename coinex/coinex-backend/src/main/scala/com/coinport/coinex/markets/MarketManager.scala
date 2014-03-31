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
import com.coinport.coinex.common.Manager
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import Implicits._
import OrderStatus._

class MarketManager(headSide: MarketSide)(implicit val now: () => Long) extends Manager[MarketState](MarketState(headSide)) {
  val MAX_TX_GROUP_SIZE = 10000
  def isOrderPriceInGoodRange(sellSide: MarketSide, price: Option[Double]): Boolean = {
    if (price.isEmpty) true
    else if (price.get <= 0) false
    else if (state.priceRestriction.isEmpty || state.orderPool(sellSide).isEmpty) true
    else if (price.get / state.orderPool(sellSide).headOption.get.price.get - 1.0 <=
      state.priceRestriction.get) true
    else false
  }

  def addOrder(sellSide: MarketSide, order: Order, txGroupId: Long): OrderSubmitted = {
    val orderWithTime = order.copy(timestamp = Some(now()))
    val txsBuffer = new ListBuffer[Transaction]

    val (totalOutAmount, totalInAmount, sellOrder, newMarket) =
      addOrderRec(sellSide.reverse, sellSide, orderWithTime, state, 0, 0, txsBuffer, txGroupId * MAX_TX_GROUP_SIZE)
    state = newMarket

    val status =
      if (sellOrder.isFullyExecuted) OrderStatus.FullyExecuted
      else if (totalOutAmount > 0) {
        if (sellOrder.price == None) OrderStatus.MarketAutoPartiallyCancelled
        else OrderStatus.PartiallyExecuted
      } else if (sellOrder.price == None) OrderStatus.MarketAutoCancelled
      else OrderStatus.Pending

    val txs = txsBuffer.toSeq
    val orderInfo = OrderInfo(sellSide, orderWithTime, totalOutAmount, totalInAmount,
      status, txs.lastOption.map(_.timestamp))

    OrderSubmitted(orderInfo, txs)
  }

  def removeOrder(side: MarketSide, id: Long, userId: Long): Option[Order] = {
    state.getOrder(id) match {
      case Some(order) if order.userId == userId => Some(order)
      case _ => None
    }
  }

  @tailrec
  private final def addOrderRec(buySide: MarketSide, sellSide: MarketSide, sellOrder: Order,
    market: MarketState, totalOutAmount: Long, totalInAmount: Long, txsBuffer: ListBuffer[Transaction],
    txId: Long): ( /*totalOutAmount*/ Long, /*totalInAmount*/ Long, /*updatedSellOrder*/ Order, /*after order match*/ MarketState) = {
    val buyOrderOption = market.orderPool(buySide).headOption
    if (buyOrderOption == None || buyOrderOption.get.vprice * sellOrder.vprice > 1) {
      // Return point. Market-price order doesn't pending
      (totalOutAmount, totalInAmount, sellOrder, if (!sellOrder.isFullyExecuted && sellOrder.price != None)
        market.addOrder(sellSide, sellOrder) else market)
    } else {
      val buyOrder = buyOrderOption.get
      val price = 1 / buyOrder.vprice
      val outAmount = Math.min(sellOrder.maxOutAmount(price), buyOrder.maxInAmount(1 / price))
      val inAmount = Math.round(outAmount * price)

      val updatedSellOrder = sellOrder.copy(
        quantity = sellOrder.quantity - outAmount, takeLimit = sellOrder.takeLimit.map(_ - inAmount))
      val updatedBuyOrder = buyOrder.copy(
        quantity = buyOrder.quantity - inAmount, takeLimit = buyOrder.takeLimit.map(_ - outAmount))
      txsBuffer += Transaction(txId, now(), sellOrder --> updatedSellOrder, buyOrder --> updatedBuyOrder)

      val leftMarket = market.removeOrder(buySide, buyOrder.id)
      if (updatedSellOrder.isFullyExecuted) {
        // return point
        (totalOutAmount + outAmount, totalInAmount + inAmount, updatedSellOrder, if (!updatedBuyOrder.isFullyExecuted)
          leftMarket.addOrder(buySide, updatedBuyOrder) else leftMarket)
      } else {
        // return point
        addOrderRec(buySide, sellSide, updatedSellOrder, leftMarket,
          totalOutAmount + outAmount, totalInAmount + inAmount, txsBuffer, txId + 1)
      }
    }
  }
}
