/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot

import org.specs2.mutable._

import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._

class RobotSpec extends Specification {
  "Robot" should {
    "simple Robot" in {
      var robot = Robot(1, 1, 1)
      robot = robot.addHandler("START") {
        """(robot -> "STATE_A", None)"""
      }
      robot = robot.addHandler("STATE_A") {
        """(robot -> "STATE_B", Some(1))"""
      }
      robot = robot.addHandler("STATE_B") {
        """(robot -> "STATE_C", Some(2))"""
      }
      robot = robot.addHandler("STATE_C") {
        """(robot -> "DONE", Some(3))"""
      }
      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      robot1.isDone mustEqual false
      val (robot2, res2) = robot1.action(None)
      res2 mustEqual Some(1)
      robot2.isDone mustEqual false
      val (robot3, res3) = robot2.action(None)
      res3 mustEqual Some(2)
      val (robot4, res4) = robot3.action(None)
      res4 mustEqual Some(3)
      robot4.isDone mustEqual true
      val (robot5, res5) = robot4.action(None)
      res5 mustEqual None
      robot5.isDone mustEqual true
    }

    "robot with state payload" in {
      val metricsImpl = Some(RobotMetrics(Map(MarketSide(Btc, Rmb) -> MarketByMetrics(MarketSide(Btc, Rmb), 123))))
      var robot = Robot(2, 1, 1)
      robot = robot.addHandler("START") {
        """
        var r = robot.setPayload("START", Some(31.9))
        r = r.setPayload("STATE_A", Some(2))
        r = r.setPayload("STATE_B", Some(2))
        (r -> "STATE_A", None)
        """
      }
      robot = robot.addHandler("STATE_A") {
        """
        require(metrics == Some(RobotMetrics(Map(MarketSide(Btc, Rmb) -> MarketByMetrics(MarketSide(Btc, Rmb), 123)))))
        val payload = robot.getPayload[Int]("STATE_A")
        val r = robot.setPayload("STATE_A", payload map { _ - 1 })
        if (payload.get != 1) {
          (r -> "STATE_A", Some(0))
        } else {
          (r -> "STATE_B", Some(1))
        }
        """
      }
      robot = robot.addHandler("STATE_B") {
        """
        require(metrics == None)
        (robot -> "STATE_C", Some(2))
        """
      }
      robot = robot.addHandler("STATE_C") {
        """
        require(metrics == None)
        val price = robot.getPayload[Double]("START").get
        require(price == 31.9)
        (robot -> "DONE", Some(3))
        """
      }
      robot.robotId mustEqual 2
      val (robot1, res1) = robot.action(None)
      res1 mustEqual None
      val (robot2, res2) = robot1.action(metricsImpl)
      res2 mustEqual Some(0)
      val (robot3, res3) = robot2.action(metricsImpl)
      res3 mustEqual Some(1)
      val (robot4, res4) = robot3.action(None)
      res4 mustEqual Some(2)
      robot4.isDone mustEqual false
      val (robot5, res5) = robot4.action(None)
      res5 mustEqual Some(3)
      robot5.isDone mustEqual true
      val (robot6, res6) = robot5.action(None)
      res6 mustEqual None
    }
  }
}
