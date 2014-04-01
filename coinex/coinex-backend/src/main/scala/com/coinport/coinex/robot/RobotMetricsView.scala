/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot

import akka.event.LoggingReceive
import akka.persistence.Persistent

import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.data._
import Implicits._

class RobotMetricsView extends ExtendedView {
  override def processorId = "coinex_mup"
  override val viewId = "metrics_view"

  val manager = new RobotMetricsManager()

  def receive = LoggingReceive {
    case DebugDump =>
      // TODO(c) extract the common logic to some common View
      log.info("state: {}", manager())
    case e @ Persistent(OrderSubmitted(orderInfo, txs), _) =>
      val sellSide = orderInfo.side.reverse
      txs.lastOption foreach { tx =>
        val Transaction(_, _, _, _, makerOrderUpdate) = tx
        makerOrderUpdate.current.price foreach { price =>
          manager.updatePrice(sellSide, price)
        }
      }
    case QueryRobotMetrics =>
      sender() ! manager()
  }
}
