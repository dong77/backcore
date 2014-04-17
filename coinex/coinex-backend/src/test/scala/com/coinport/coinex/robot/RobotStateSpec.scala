package com.coinport.coinex.robot

import org.specs2.mutable._
import com.coinport.coinex.data.RobotState
import com.coinport.coinex.data._
import com.coinport.coinex.robot.sample.StopOrderRobot
import com.coinport.coinex.data.Currency._
import Implicits._

class RobotStateSpec extends Specification {

  "robot state" should {
    "add and remove robot brain" in {

      val robotState = new RobotState(
        RobotState.EmptyRobotPool,
        Map.empty[Long, Robot],
        Metrics(),
        Map.empty[Long, RobotBrain])

      val (payload, brain) = StopOrderRobot(10, 2, 10000, 3125.0, (Btc ~> Rmb), Order(1, 1, 2, Some(3429.0)))
      val (brainId, robotState2) = robotState.addRobotBrain(brain)

      robotState2.robotBrainMap.contains(brainId) mustEqual true
      robotState2.isExistRobotBrain(brain) mustEqual true

      val (brainId2, robotState3) = robotState2.addRobotBrain(brain)
      brainId2 mustEqual brainId
      robotState3.getUsingRobots(brainId).contains(10) mustEqual false
      robotState3.isExistRobotBrain(brain) mustEqual true

      val robotState4 = robotState3.addRobot(Robot(10, 1, 1, payload, "START", brainId))
      robotState4.getUsingRobots(brainId).contains(10) mustEqual true

      val robotState5 = robotState4.removeRobotBrain(brainId)
      robotState5.robotBrainMap.contains(brainId) mustEqual true
      val robotState6 = robotState5.removeRobot(10)
      robotState6.getUsingRobots(brainId).contains(10) mustEqual false
      val robotState7 = robotState6.removeRobotBrain(brainId)
      robotState7.robotBrainMap.contains(brainId) mustEqual false
    }
  }

}