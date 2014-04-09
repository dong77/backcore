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
import com.coinport.coinex.data.mutable.MarketState
import com.coinport.coinex.common.Manager
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import Implicits._
import OrderStatus._

class MarketManager(headSide: MarketSide) extends Manager[MarketState](MarketState(headSide)) {
  val MAX_TX_GROUP_SIZE = 10000
  def isOrderPriceInGoodRange(takerSide: MarketSide, price: Option[Double]): Boolean = {
    if (price.isEmpty) true
    else if (price.get <= 0) false
    else if (state.priceRestriction.isEmpty || state.orderPool(takerSide).isEmpty) true
    else if (price.get / state.orderPool(takerSide).headOption.get.price.get - 1.0 <=
      state.priceRestriction.get) true
    else false
  }

  def orderExist(orderId: Long) = state.getOrder(orderId).isDefined

  def addOrder(takerSide: MarketSide, order: Order): OrderSubmitted = {
    val txsBuffer = new ListBuffer[Transaction]

    val (totalOutAmount, totalInAmount, takerOrder, newMarket) =
      addOrderRec(takerSide.reverse, takerSide, order, state, 0, 0, txsBuffer, order.id * MAX_TX_GROUP_SIZE)
    state = newMarket

    val status =
      if (takerOrder.isFullyExecuted) OrderStatus.FullyExecuted
      else if (totalOutAmount > 0) {
        if (takerOrder.price == None || takerOrder.onlyTaker.getOrElse(false)) OrderStatus.MarketAutoPartiallyCancelled
        else OrderStatus.PartiallyExecuted
      } else if (takerOrder.price == None || takerOrder.onlyTaker.getOrElse(false)) OrderStatus.MarketAutoCancelled
      else OrderStatus.Pending

    val txs = txsBuffer.toSeq
    val orderInfo = OrderInfo(takerSide, order, totalOutAmount, totalInAmount,
      status, txs.lastOption.map(_.timestamp))

    OrderSubmitted(orderInfo, txs)
  }

  def removeOrder(side: MarketSide, id: Long, userId: Long): Order = {
    val order = state.getOrder(id).get
    state = state.removeOrder(side, id)
    order
  }

  @tailrec
  private final def addOrderRec(makerSide: MarketSide, takerSide: MarketSide, takerOrder: Order,
    market: MarketState, totalOutAmount: Long, totalInAmount: Long, txsBuffer: ListBuffer[Transaction],
    txId: Long): ( /*totalOutAmount*/ Long, /*totalInAmount*/ Long, /*updatedSellOrder*/ Order, /*after order match*/ MarketState) = {
    val makerOrderOption = market.orderPool(makerSide).headOption
    if (makerOrderOption == None || makerOrderOption.get.vprice * takerOrder.vprice > 1) {
      // Return point. Market-price order doesn't pending
      (totalOutAmount, totalInAmount, takerOrder, if (!takerOrder.isFullyExecuted && takerOrder.price != None &&
        !takerOrder.onlyTaker.getOrElse(false)) market.addOrder(takerSide, takerOrder) else market)
    } else {
      val makerOrder = makerOrderOption.get
      val price = 1 / makerOrder.vprice
      val lvOutAmount = Math.min(takerOrder.maxOutAmount(price), makerOrder.maxInAmount(1 / price))
      val lvInAmount = Math.round(lvOutAmount * price)

      val updatedSellOrder = takerOrder.copy(quantity = takerOrder.quantity - lvOutAmount,
        takeLimit = takerOrder.takeLimit.map(_ - lvInAmount), inAmount = takerOrder.inAmount + lvInAmount)
      val updatedBuyOrder = makerOrder.copy(quantity = makerOrder.quantity - lvInAmount,
        takeLimit = makerOrder.takeLimit.map(_ - lvOutAmount), inAmount = makerOrder.inAmount + lvOutAmount)
      txsBuffer += Transaction(txId, takerOrder.timestamp.getOrElse(0), takerSide,
        takerOrder --> updatedSellOrder, makerOrder --> updatedBuyOrder)

      val leftMarket = market.removeOrder(makerSide, makerOrder.id)
      if (updatedSellOrder.isFullyExecuted) {
        // return point
        (totalOutAmount + lvOutAmount, totalInAmount + lvInAmount, updatedSellOrder, if (!updatedBuyOrder.isFullyExecuted)
          leftMarket.addOrder(makerSide, updatedBuyOrder) else leftMarket)
      } else {
        // return point
        addOrderRec(makerSide, takerSide, updatedSellOrder, leftMarket,
          totalOutAmount + lvOutAmount, totalInAmount + lvInAmount, txsBuffer, txId + 1)
      }
    }
  }
}
