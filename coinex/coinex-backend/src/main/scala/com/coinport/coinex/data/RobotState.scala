/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.data

import scala.collection.immutable.SortedSet

object RobotState {
  implicit val ordering = new Ordering[Robot] {
    def compare(lhs: Robot, rhs: Robot) = if (lhs.robotId < rhs.robotId) -1 else if (lhs.robotId > rhs.robotId) 1 else 0
  }

  val EmptyRobotPool = SortedSet.empty[Robot]
}

case class RobotState(
    robotPool: SortedSet[Robot] = RobotState.EmptyRobotPool,
    robotMap: Map[Long, Robot] = Map.empty[Long, Robot],
    metrics: RobotMetrics = RobotMetrics()) {

  def getRobot(id: Long): Option[Robot] = robotMap.get(id)

  def getRobotPool = robotPool

  def addRobot(robot: Robot): RobotState = {
    copy(robotPool = robotPool + robot, robotMap = robotMap + (robot.robotId -> robot))
  }

  def removeRobot(rid: Long): RobotState = {
    robotMap.get(rid) match {
      case Some(robot) =>
        copy(robotPool = robotPool - robot, robotMap = robotMap - robot.robotId)
      case _ => this
    }
  }

  def updateMetrics(m: RobotMetrics): RobotState = {
    copy(metrics = m)
  }
}
