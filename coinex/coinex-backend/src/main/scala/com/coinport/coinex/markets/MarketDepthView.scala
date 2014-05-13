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
import scala.collection.mutable.ListBuffer

class MarketDepthView(market: MarketSide) extends ExtendedView {
  override val processorId = MARKET_PROCESSOR << market
  override val viewId = MARKET_DEPTH_VIEW << market

  case class Cached(depth: Int, asks: Seq[MarketDepthItem], bids: Seq[MarketDepthItem])

  val manager = new MarketManager(market)
  private var cache: Option[Cached] = None

  def receive = LoggingReceive {
    case Persistent(DoCancelOrder(_, orderId, userId), _) =>
      manager.removeOrder(orderId, userId)
      cache = None

    case Persistent(OrderFundFrozen(side, order: Order), _) =>
      manager.addOrderToMarket(side, order)
      cache = None

    case QueryMarketDepth(side, depth) =>
      assert(side == market)
      if (cache.isEmpty || cache.get.depth < depth) cache = Some(getDepthData(depth))
      val cached = cache.get
      sender ! QueryMarketDepthResult(MarketDepth(market, cached.asks.take(depth), cached.bids.take(depth)))

    case DoSimulateOrderSubmission(DoSubmitOrder(side, order)) =>
      val state = manager.getState()
      val orderSubmitted = manager.addOrderToMarket(side, order)
      manager.loadState(state)
      sender ! OrderSubmissionSimulated(orderSubmitted)
  }

  private def getDepthData(depth: Int) = {
    def takeN(orders: SortedSet[Order], isAskOrder: Boolean) = {

      def convert(order: Order) =
        if (isAskOrder) MarketDepthItem(order.price.get, order.maxOutAmount(order.price.get))
        else /* bid */ MarketDepthItem((1 / order.price.get).!!!, order.maxInAmount(order.price.get))

      val buffer = new ListBuffer[MarketDepthItem]
      var index = 0
      while (buffer.size < depth && index < orders.size) {
        val order = orders.view(index, index + 1).head
        val item = convert(order)
        if (buffer.isEmpty || buffer.last.price != order.price.get) buffer += item
        else {
          val last = buffer.last
          buffer.trimEnd(1)
          buffer += last.copy(quantity = last.quantity + item.quantity)
        }
        index += 1
      }
      buffer.toSeq
    }

    val asks = takeN(manager.orderPool(market), true)
    val bids = takeN(manager.orderPool(market.reverse), false)
    Cached(depth, asks, bids)
  }
}
