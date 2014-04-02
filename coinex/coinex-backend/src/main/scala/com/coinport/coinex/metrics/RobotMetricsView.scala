/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
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
      // TODO(c) extract the common logic to some common View
      log.info("state: {}", manager())
    case e @ Persistent(OrderSubmitted(orderInfo, txs), _) =>
      val sellSide = orderInfo.side.reverse
      txs.lastOption foreach { tx =>
        val Transaction(_, _, _, _, makerOrderUpdate, _) = tx
        makerOrderUpdate.current.price foreach { price =>
          manager.updatePrice(sellSide, price)
        }
      }
    case QueryRobotMetrics =>
      sender() ! manager()
  }
}
