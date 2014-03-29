/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot.sample

import org.specs2.mutable._

import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import Implicits._

class StopOrderRobotSpec extends Specification {
  "stop order robot" should {
    "stop order robot for sell btc" in {
      val robot = StopOrderRobot(1, 2, 10000, 3125.0, (Btc ~> Rmb), Order(1, 1, 2, Some(3429.0)))
      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      val (robot2, res2) = robot1.action(None)
      res2 mustEqual None
      val (robot3, res3) = robot2.action(
        Some(RobotMetrics(Map((Btc ~> Rmb) -> MarketByMetrics((Btc ~> Rmb), 3421.0))))
      )
      res3 mustEqual None
      val (robot4, res4) = robot3.action(
        Some(RobotMetrics(Map((Btc ~> Rmb) -> MarketByMetrics((Btc ~> Rmb), 2000.0))))
      )
      res4 mustEqual Some(DoSubmitOrder((Btc ~> Rmb), Order(2, 1, 2, Some(3429.0), robotId = Some(1))))
      robot4.isDone mustEqual true
    }

    "stop order robot for buy btc" in {
      val robot = StopOrderRobot(1, 2, 10000, 1 / 3125.0, (Rmb ~> Btc), Order(1, 1, 2 * 3000, Some(1 / 3000.0)))
      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      val (robot2, res2) = robot1.action(None)
      res2 mustEqual None
      val (robot3, res3) = robot2.action(
        Some(RobotMetrics(Map((Rmb ~> Btc) -> MarketByMetrics((Rmb ~> Btc), 1 / 3111.0))))
      )
      res3 mustEqual None
      val (robot4, res4) = robot3.action(
        Some(RobotMetrics(Map((Rmb ~> Btc) -> MarketByMetrics((Rmb ~> Btc), 1 / 4000.0))))
      )
      res4 mustEqual Some(DoSubmitOrder((Rmb ~> Btc), Order(2, 1, 2 * 3000, Some(1 / 3000.0), robotId = Some(1))))
      robot4.isDone mustEqual true
    }
  }
}
