/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

object OrderDataState {
  type ItemMap = Map[Long, OrderInfo]
  val emptyItemMap = Map.empty[Long, OrderInfo]
  val ARCHIVE_SIZE = 50000
  val MAX_MAINTAIN_SIZE = 150000
}

case class OrderDataState(itemMap: OrderDataState.ItemMap = OrderDataState.emptyItemMap) {
  def addItem(orderId: Long, orderInfo: OrderInfo) = {
    copy(itemMap = (itemMap + (orderId -> orderInfo)))
  }

  def updateItem(orderId: Long, neu: OrderInfo) = {
    val keyValue = itemMap.get(orderId) match {
      case None => orderId -> neu
      case Some(old) =>
        orderId -> old.copy(
          status = neu.status,
          outAmount = old.outAmount + neu.outAmount,
          inAmount = old.inAmount + neu.inAmount,
          lastTxTimestamp = neu.lastTxTimestamp)
    }

    copy(itemMap = (itemMap + keyValue))
  }

  def cancelItem(orderId: Long) = {
    val neu = itemMap.get(orderId) match {
      case Some(order) => order.copy(status = OrderStatus.Cancelled)
      case None => null
    }
    if (neu != null) copy(itemMap = itemMap + (orderId -> neu))
    else this
  }

  def getItem(orderId: Option[Long], from: Long, to: Long): Seq[OrderInfo] = {
    if (orderId.isDefined) Seq(itemMap.getOrElse(orderId.get, null)).filter(_ != null)
    else itemMap.slice(from.toInt, to.toInt).values.toSeq
  }
}
