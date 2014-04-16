/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.data

import scala.collection.immutable.SortedSet
import com.twitter.util.Eval
import com.coinport.coinex.common.Constants._
import org.slf4s.Logging

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
    robotModelMap: Map[Long, Map[String, Action]] = Map.empty[Long, Map[String, Action]]) extends Object with Logging {

  def getRobot(id: Long): Option[Robot] = robotMap.get(id)

  def getRobotPool = robotPool

  def addRobot(robot: Robot): RobotState = {
    log.debug("[ADD ROBOT] robotId: %d, modelId: %d".format(robot.robotId, robot.modelId))
    copy(robotPool = robotPool + robot, robotMap = robotMap + (robot.robotId -> robot))
  }

  def addRobotModel(states: scala.collection.immutable.Map[String, String]): RobotState = {
    var stateAction: Map[String, Action] = states map { state =>
      (state._1 -> inflate(state._2))
    }
    log.debug("[ADD ROBOT MODEL] id: %d, state: %s".format(robotModelMap.size, stateAction.keySet.mkString(",")))
    copy(robotModelMap = robotModelMap + (robotModelMap.size.toLong -> stateAction))
  }

  def updateRobotModel(modelId: Long, states: scala.collection.immutable.Map[String, String]): RobotState = {
    robotModelMap - modelId
    var stateAction: Map[String, Action] = states map { state =>
      (state._1 -> inflate(state._2))
    }
    copy(robotModelMap = robotModelMap + (modelId -> stateAction))
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
