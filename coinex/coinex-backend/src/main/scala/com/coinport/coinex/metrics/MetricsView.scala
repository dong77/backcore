/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.metrics

import akka.event.LoggingReceive
import akka.persistence.Persistent

import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import Implicits._

class MetricsView extends ExtendedView {
  override def processorId = MARKET_UPDATE_PROCESSOR <<
  override val viewId = METRICS_VIEW <<

  val manager = new MetricsManager()

  def receive = LoggingReceive {
    case e @ Persistent(OrderSubmitted(_, txs), _) =>
      txs foreach { tx =>
        val Transaction(_, _, _, _, makerOrderUpdate, _) = tx
        makerOrderUpdate.current.price foreach { price =>
          manager.update(tx.side, price.reciprocal,
            makerOrderUpdate.current.inAmount - makerOrderUpdate.previous.inAmount,
            makerOrderUpdate.previous.quantity - makerOrderUpdate.current.quantity,
            tx.timestamp)
        }
      }
    case QueryMetrics =>
      sender() ! manager.getMetrics(System.currentTimeMillis)
  }
}
