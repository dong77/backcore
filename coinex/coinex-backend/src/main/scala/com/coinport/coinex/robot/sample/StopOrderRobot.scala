/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot.sample

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import Implicits._

object StopOrderRobot {
  def apply(robotId: Long, userId: Long, timestamp: Long,
    stopPrice: Double, side: MarketSide, order: Order) = {
    val brain = Map(
      "START" -> """
        val r = robot.setPayload("SP", Some(%f))
          .setPayload("SIDE", Some(%s)).setPayload("ORDER", Some(%s))
        (r -> "LISTENING", None)
      """.format(stopPrice, side.toString, order.toString),

      "LISTENING" -> """
        val stopPrice = robot.getPayload[Double]("SP").get
        val side = robot.getPayload[MarketSide]("SIDE").get
        val order = robot.getPayload[Order]("ORDER").get
        metrics match {
          case Some(m) => m.marketByMetrics.get(side) match {
            case Some(mbm) if (mbm.price <= stopPrice) =>
              val action = Some(DoSubmitOrder(side, order.copy(userId = robot.userId, robotId = Some(robot.robotId))))
              (robot -> "DONE", action)
            case _ => (robot -> "LISTENING", None)
          }
          case _ => (robot -> "LISTENING", None)
        }
      """
    )

    Robot(robotId, userId, timestamp, brain)
  }
}
