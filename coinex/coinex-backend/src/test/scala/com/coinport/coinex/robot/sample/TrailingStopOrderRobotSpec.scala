/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot.sample

import org.specs2.mutable._

import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import Implicits._

class TrailingStopOrderRobotSpec extends Specification {
  "stop order robot" should {
    "trailing stop order robot for sell btc without trailing" in {
      val robot = TrailingStopOrderRobot(1, 2, 10000, 4000.0, 0.1 /* 10% */ , (Btc ~> Rmb),
        Order(1, 1, 2, Some(3429.0)))
      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      val (robot2, res2) = robot1.action(None)
      res2 mustEqual None
      val (robot3, res3) = robot2.action(
        Some(Metrics(Map((Btc ~> Rmb) -> MetricsByMarket((Btc ~> Rmb), 3601.0))))
      )
      res3 mustEqual None
      val (robot4, res4) = robot3.action(
        Some(Metrics(Map((Btc ~> Rmb) -> MetricsByMarket((Btc ~> Rmb), 3600.0))))
      )
      res4 mustEqual Some(DoSubmitOrder((Btc ~> Rmb),
        Order(2, 1, 2, Some(3429.0), robotId = Some(1), robotType = Some(2))))
      robot4.isDone mustEqual true
    }

    "trailing stop order robot for sell btc with trailing" in {
      val robot = TrailingStopOrderRobot(1, 2, 10000, 4000.0, 0.1 /* 10% */ , (Btc ~> Rmb),
        Order(1, 1, 2, Some(3429.0)))
      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      val (robot2, res2) = robot1.action(None)
      res2 mustEqual None
      val (robot3, res3) = robot2.action(
        Some(Metrics(Map((Btc ~> Rmb) -> MetricsByMarket((Btc ~> Rmb), 3601.0))))
      )
      res3 mustEqual None
      val (robot4, res4) = robot3.action(
        Some(Metrics(Map((Btc ~> Rmb) -> MetricsByMarket((Btc ~> Rmb), 5000.0))))
      )
      res4 mustEqual None
      val (robot5, res5) = robot4.action(
        Some(Metrics(Map((Btc ~> Rmb) -> MetricsByMarket((Btc ~> Rmb), 4501.0))))
      )
      res5 mustEqual None
      val (robot6, res6) = robot5.action(
        Some(Metrics(Map((Btc ~> Rmb) -> MetricsByMarket((Btc ~> Rmb), 4500.0))))
      )
      res6 mustEqual Some(DoSubmitOrder((Btc ~> Rmb),
        Order(2, 1, 2, Some(3429.0), robotId = Some(1), robotType = Some(2))))
      robot6.isDone mustEqual true
    }

    "trailing stop order robot for buy btc without trailing" in {
      val robot = TrailingStopOrderRobot(1, 2, 10000, 1 / 4000.0, 0.2 /* 25% not typo! */ , (Rmb ~> Btc),
        Order(1, 1, 2 * 4200, Some(1 / 4200.0)))
      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      val (robot2, res2) = robot1.action(None)
      res2 mustEqual None
      val (robot3, res3) = robot2.action(
        Some(Metrics(Map((Rmb ~> Btc) -> MetricsByMarket((Rmb ~> Btc), 1 / 4999.0))))
      )
      res3 mustEqual None
      val (robot4, res4) = robot3.action(
        Some(Metrics(Map((Rmb ~> Btc) -> MetricsByMarket((Rmb ~> Btc), 1 / 5000.0))))
      )
      res4 mustEqual Some(DoSubmitOrder((Rmb ~> Btc),
        Order(2, 1, 2 * 4200, Some(1 / 4200.0), robotId = Some(1), robotType = Some(2))))
      robot4.isDone mustEqual true
    }

    "trailing stop order robot for buy btc with trailing" in {
      val robot = TrailingStopOrderRobot(1, 2, 10000, 1 / 4000.0, 0.2 /* 10% */ , (Rmb ~> Btc),
        Order(1, 1, 2 * 10000, Some(1 / 10000.0)))

      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      val (robot2, res2) = robot1.action(None)
      res2 mustEqual None
      val (robot3, res3) = robot2.action(
        Some(Metrics(Map((Rmb ~> Btc) -> MetricsByMarket((Rmb ~> Btc), 1 / 4500.0))))
      )
      res3 mustEqual None
      val (robot4, res4) = robot3.action(
        Some(Metrics(Map((Rmb ~> Btc) -> MetricsByMarket((Rmb ~> Btc), 1 / 2000.0))))
      )
      res4 mustEqual None
      val (robot5, res5) = robot4.action(
        Some(Metrics(Map((Rmb ~> Btc) -> MetricsByMarket((Rmb ~> Btc), 1 / 2499.0))))
      )
      res5 mustEqual None
      val (robot6, res6) = robot5.action(
        Some(Metrics(Map((Rmb ~> Btc) -> MetricsByMarket((Rmb ~> Btc), 1 / 2500.0))))
      )
      res6 mustEqual Some(DoSubmitOrder((Rmb ~> Btc),
        Order(2, 1, 2 * 10000, Some(1 / 10000.0), robotId = Some(1), robotType = Some(2))))
      robot6.isDone mustEqual true
    }
  }
}
