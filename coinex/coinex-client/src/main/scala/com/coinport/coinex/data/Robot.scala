/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.data

import com.twitter.util.Eval

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._

// TODO(c) use cache for state handler instead of inflating it from string every time
// we need change 'robot' in user's code to r inorder to hide the immutable such as:
// if user wrote: robot.setPayload("A", "1") we need change to r = robot.setPayload("A", "1")
case class Robot(
    robotId: Long, userId: Long = COINPORT_UID, timestamp: Long = 0,
    states: Map[String, String] = Map.empty[String, String],
    statesPayload: Map[String, Option[Any]] = Map.empty[String, Option[Any]],
    currentState: String = "START") {

  // Option[Any] is the actual action of the robot, such as DoDepositCash.
  // This could be restrained from outter processor
  // TODO(c): try to make RobotMetrics as T
  type Action = (Robot, Option[RobotMetrics]) => (Robot, Option[Any])

  private val START = "START"
  private val DONE = "DONE"
  private val HEADER = """
    import com.coinport.coinex.data._
    import com.coinport.coinex.data.Currency._
    (robot: Robot, metrics: Option[RobotMetrics]) =>

  """

  // invoked by outter processor
  def action(metrics: Option[RobotMetrics] = None): (Robot, Option[Any]) = {
    // TODO(c): check existance
    if (currentState == DONE)
      (this, None)
    else {
      val function = inflate(HEADER + states(currentState))
      function(this, metrics)
    }
  }

  def isDone = currentState == DONE

  def addHandler(state: String)(handler: String): Robot = {
    require(!states.contains(state), "can't set multiple handlers to one state")
    require(state != null)
    copy(states = states + (state -> handler))
  }

  def getPayload[T](state: String) =
    Option(statesPayload.getOrElse(state, None).getOrElse(null).asInstanceOf[T])

  def setPayload(state: String, payload: Option[Any]): Robot = {
    copy(statesPayload = statesPayload + (state -> payload))
  }

  def ->(state: String): Robot = { copy(currentState = state) }

  private def inflate(source: String): Action = {
    (new Eval()(source)).asInstanceOf[Action]
  }
}
