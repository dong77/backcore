/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView
import akka.persistence.Persistent
import com.coinport.coinex.data._
import Implicits._

class RobotView extends ExtendedView {
  override val processorId = "coinex_rp"
  override val viewId = "coinex_robot_view"
  val manager = new RobotManager()
  def receive = LoggingReceive {
    case DumpToFile =>
      log.info("state: {}", manager())
  }
}
