/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.common._
import Implicits._
import scala.collection.SortedSet
import scala.collection.mutable.{ ListBuffer, Map }

class MarketDepthView(market: MarketSide) extends ExtendedView {
  override val processorId = MARKET_PROCESSOR << market
  override val viewId = MARKET_DEPTH_VIEW << market

  case class Cached(depth: Int, asks: Seq[MarketDepthItem], bids: Seq[MarketDepthItem])

  val manager = new MarketManager(market)
  private var cacheMap = Map.empty[MarketSide, Cached]

  def receive = LoggingReceive {
    case Persistent(DoCancelOrder(_, orderId, userId), _) =>
      manager.removeOrder(orderId, userId)
      cacheMap = Map.empty[MarketSide, Cached]

    case Persistent(OrderFundFrozen(side, order: Order), _) =>
      manager.addOrderToMarket(side, order)
      cacheMap = Map.empty[MarketSide, Cached]

    case QueryMarketDepth(side, depth) =>
      cacheMap.get(side) match {
        case Some(cached) if cached.depth >= depth =>
        case _ => cacheMap += side -> getDepthData(side, depth)
      }
      sender ! QueryMarketDepthResult(MarketDepth(side, cacheMap(side).asks.take(depth), cacheMap(side).bids.take(depth)))

    case DoSimulateOrderSubmission(DoSubmitOrder(side, order)) =>
      val state = manager.getState()
      val orderSubmitted = manager.addOrderToMarket(side, order)
      manager.loadState(state)
      sender ! OrderSubmissionSimulated(orderSubmitted)
  }

  private def getDepthData(side: MarketSide, depth: Int) = {
    def takeN(orders: SortedSet[Order], isAsk: Boolean) = {

      def convert(order: Order) =
        if (isAsk) MarketDepthItem(order.price.get.value, order.maxOutAmount(order.price.get))
        else /* bid */ MarketDepthItem(order.price.get.reciprocal.value, order.maxInAmount(order.price.get))

      val buffer = new ListBuffer[MarketDepthItem]
      var index = 0
      while (buffer.size < depth && index < orders.size) {
        val order = orders.view(index, index + 1).head
        val item = convert(order)
        if (buffer.isEmpty || buffer.last.price != item.price) buffer += item
        else {
          val last = buffer.last
          buffer.trimEnd(1)
          buffer += last.copy(quantity = last.quantity + item.quantity)
        }
        index += 1
      }
      buffer.toSeq
    }

    val asks = takeN(manager.orderPool(side), true)
    val bids = takeN(manager.orderPool(side.reverse), false)

    Cached(depth, asks, bids)
  }
}
