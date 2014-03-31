/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import com.coinport.coinex.common._
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import akka.persistence.Persistent
import Implicits._
import com.mongodb.casbah.{ MongoDB, MongoCollection }

class OrderView(market: MarketSide, db: MongoDB) extends ExtendedView {
  override val processorId = "coinex_mup"
  override val viewId = "orders_view"

  private val coll = db("order_" + market.toString)
  private val manager = new OrderManager(market, coll)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderCancelled(_, order), _) => manager().cancelItem(order.id)

    case Persistent(m: OrderSubmitted, _) if (m.originOrderInfo.side == market || m.originOrderInfo.side == market.reverse) =>
      manager.addOrder(m.originOrderInfo)
      m.txs.foreach { tx =>
        val outAmount = tx.makerUpdate.previous.quantity - tx.makerUpdate.current.quantity
        val inAmount = tx.takerUpdate.previous.quantity - tx.takerUpdate.current.quantity
        val status =
          if (tx.makerUpdate.current.isFullyExecuted) OrderStatus.FullyExecuted
          else OrderStatus.PartiallyExecuted

        manager().updateItem(tx.makerUpdate.current.id, inAmount, outAmount, status.getValue(), tx.timestamp)
      }

    case q: QueryOrder =>
      sender ! QueryOrderResult(manager().getItems(q), manager().countItems(q))
  }
}

class OrderManager(side: MarketSide, coll: MongoCollection) extends Manager[OrderDataState](OrderDataState(coll)) {
  def addOrder(oi: OrderInfo) = {
    val orderItem = OrderItem(oid = oi.order.id, uid = oi.order.userId, inAmount = oi.inAmount,
      outAmount = oi.outAmount, originOrder = oi.order, sameSide = oi.side == side, status = oi.status.getValue(),
      timestamp = oi.lastTxTimestamp.getOrElse(oi.order.timestamp.getOrElse(0)))
    state.addItem(orderItem)
  }
}
