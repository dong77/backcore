/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot.sample

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import Implicits._

class StopOrderRobot(robotId: Long, userId: Long = COINPORT_UID, timestamp: Long = 0,
    val stopPrice: Double, val side: MarketSide, val order: Order) extends Robot(robotId, userId, timestamp) {
  object SORState extends Enumeration {
    val LISTENING = Value
  }

  import com.coinport.coinex.data.State._
  import SORState._

  override def firstState = LISTENING

  override def inflate {
    addHandler(LISTENING) { m =>
      m match {
        case Some(metrics) => metrics.marketByMetrics.get(side) match {
          case Some(mbm) if (mbm.price <= stopPrice) =>
            (Some(DoSubmitOrder(side, order.copy(userId = this.userId, robotId = Some(this.robotId)))), DONE)
          case _ => (None, LISTENING)
        }
        case _ => (None, LISTENING)
      }
    }
  }
}
