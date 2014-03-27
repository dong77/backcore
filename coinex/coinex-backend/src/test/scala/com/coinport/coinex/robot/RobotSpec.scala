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
    "simple robot" in {
      class TestRobot(rid: Long) extends Robot(rid) {
        object TState extends Enumeration {
          val STATE_A, STATE_B, STATE_C = Value
        }

        import State._
        import TState._

        override def firstState = STATE_A

        override def inflate {
          addHandler(STATE_A) { metrics =>
            (Some(1), STATE_B)
          }

          addHandler(STATE_B) { metrics =>
            (Some(2), STATE_C)
          }

          addHandler(STATE_C) { metrics =>
            (Some(3), DONE)
          }
        }
      }

      val robot = new TestRobot(1)
      robot.robotId mustEqual 1
      robot.action(None) mustEqual Some(1)
      robot.isDone mustEqual false
      robot.action(None) mustEqual Some(2)
      robot.action(None) mustEqual Some(3)
      robot.isDone mustEqual true
      robot.action(None) mustEqual None
      robot.isDone mustEqual true
    }

    "robot with state payload" in {
      val metricsImpl = Some(RobotMetrics(Map(MarketSide(Btc, Rmb) -> MarketByMetrics(MarketSide(Btc, Rmb), 123))))
      class TestRobot(rid: Long) extends Robot(rid) {
        object TState extends Enumeration {
          val STATE_A, STATE_B, STATE_C = Value
        }

        import State._
        import TState._

        override def firstState = STATE_A

        override def inflate {
          addPayload(STATE_A, Some(2))
          addPayload(STATE_B, Some(2))

          addHandler(STATE_A) { metrics =>
            metrics mustEqual metricsImpl
            val payload = getPayload[Int]()
            setPayload(payload map { _ - 1 })
            if (payload.get != 1) {
              (Some(0), STATE_A)
            } else {
              (Some(1), STATE_B)
            }
          }
          addHandler(STATE_B) { metrics =>
            metrics mustEqual None
            (Some(2), STATE_C)
          }
          addHandler(STATE_C) { metrics =>
            metrics mustEqual None
            (Some(3), DONE)
          }
        }
      }

      val robot = new TestRobot(2)
      robot.robotId mustEqual 2
      robot.action(metricsImpl) mustEqual Some(0)
      robot.action(metricsImpl) mustEqual Some(1)
      robot.action(None) mustEqual Some(2)
      robot.isDone mustEqual false
      robot.action(None) mustEqual Some(3)
      robot.isDone mustEqual true
      robot.action(None) mustEqual None
    }
  }
}
