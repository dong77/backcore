/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot

import com.coinport.coinex.data._
import com.coinport.coinex.common.Manager
import Implicits._

class RobotManager extends Manager[RobotState](RobotState()) {

  def addRobot(robot: Robot) {
    state = state.addRobot(robot)
  }

  def removeRobot(id: Long): Option[Robot] = {
    val robot = state.getRobot(id)
    robot foreach {
      _ => state = state.removeRobot(id)
    }
    robot
  }

  def updateMetrics(m: Metrics) {
    state = state.updateMetrics(m)
  }
}
