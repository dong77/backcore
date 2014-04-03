/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 *
 * TODO(c) need call MetricsState snapshot method when snapshoting
 */

package com.coinport.coinex.metrics

import akka.event.LoggingReceive
import akka.persistence.Persistent

import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.data._
import Implicits._

class MetricsView extends ExtendedView {
  override def processorId = "coinex_mup"
  override val viewId = "metrics_view"

  val manager = new MetricsManager()

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())
    case e @ Persistent(OrderSubmitted(orderInfo, txs), _) =>
      txs.lastOption foreach { tx =>
        val Transaction(_, _, _, _, makerOrderUpdate, _) = tx
        makerOrderUpdate.current.price foreach { price =>
          manager.update(orderInfo.side, 1 / price, orderInfo.outAmount, orderInfo.inAmount, System.currentTimeMillis)
        }
      }
    case QueryMetrics =>
      sender() ! manager.getMetrics(System.currentTimeMillis)
  }
}
