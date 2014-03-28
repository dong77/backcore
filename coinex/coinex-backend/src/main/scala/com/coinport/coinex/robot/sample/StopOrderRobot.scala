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
        var r = robot.setPayload("SP", %f).setPayload("SIDE", %s).setPayload("ORDER", %s)
        (robot -> "LISTENING", None)
      """.format(stopPrice, side.toString, order.toString),

      "LOOP" -> """
        import scala.util.Random
        val btcSide = MarketSide(Btc, Rmb)
        val rmbSide = MarketSide(Rmb, Btc)
        val side = List(btcSide, rmbSide)(Random.nextInt(2))
        val price = metrics match {
          case None => if (side == btcSide) 3000.0 else 1 / 3000.0
          case Some(m) => m.marketByMetrics.get(side) match {
            case Some(mbm) => mbm.price
            case _ => if (side == btcSide) 3000.0 else 1 / 3000.0
          }
        }

        val range = 100.0 - Random.nextInt(100)
        val orderPrice = price * (1 + range / 100.0)
        var quantity = 10 * (1 + range / 100.0)
        if (side == rmbSide) quantity /= orderPrice
        val action = Some(DoSubmitOrder(side,
          Order(robot.userId, 0, quantity.toLong, price = Some(orderPrice), robotId = Some(robot.robotId))))

        (robot -> "LOOP", action)
      """
    )

    Robot(robotId, userId, timestamp, brain)
  }
}

/*
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
*/
