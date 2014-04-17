/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.data

import scala.collection.immutable.SortedSet
import com.twitter.util.Eval
import com.coinport.coinex.common.Constants._
import org.slf4s.Logging
import com.coinport.coinex.robot.RobotBrain
import com.coinport.coinex.robot.RobotBrain

object RobotState {
  implicit val ordering = new Ordering[Robot] {
    def compare(lhs: Robot, rhs: Robot) = if (lhs.robotId < rhs.robotId) -1 else if (lhs.robotId > rhs.robotId) 1 else 0
  }

  val EmptyRobotPool = SortedSet.empty[Robot]

}

case class RobotState(
    robotPool: SortedSet[Robot] = RobotState.EmptyRobotPool,
    robotMap: Map[Long, Robot] = Map.empty[Long, Robot],
    metrics: Metrics = Metrics(),
    robotBrainMap: Map[String, RobotBrain] = Map.empty[String, RobotBrain]) extends Object with Logging {

  def getRobot(id: Long): Option[Robot] = robotMap.get(id)

  def getRobotPool = robotPool

  def addRobot(robot: Robot): RobotState = {
    log.debug("[ADD ROBOT] robotId: %d, brainId: %s".format(robot.robotId, robot.brainId))
    copy(robotPool = robotPool + robot, robotMap = robotMap + (robot.robotId -> robot))
  }

  def addRobotBrain(states: scala.collection.immutable.Map[String, String]): (String, RobotState) = {
    var stateAction: Map[String, Action] = states map { state =>
      (state._1 -> inflate(state._2))
    }
    val brainId = stateAction.hashCode.toString
    log.debug("[ADD ROBOT BRAIN] id: %d, state: %s".format(robotBrainMap.size, stateAction.keySet.mkString(",")))
    (brainId, copy(robotBrainMap = robotBrainMap + (stateAction.hashCode.toString -> RobotBrain(brainId, stateAction))))
  }

  def removeRobot(rid: Long): RobotState = {
    robotMap.get(rid) match {
      case Some(robot) =>
        copy(robotPool = robotPool - robot, robotMap = robotMap - robot.robotId)
      case _ => this
    }
  }

  def updateMetrics(m: Metrics): RobotState = {
    copy(metrics = m)
  }

  def inflate(source: String): Action = {

    val HEADER = """
      import com.coinport.coinex.data._
      import com.coinport.coinex.data.Currency._
      (robot: Robot, metrics: Option[Metrics]) =>

    """
    (new Eval()(HEADER + source)).asInstanceOf[Action]
  }
}
