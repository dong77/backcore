/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.data

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._

object State extends Enumeration {
  val START, DONE = Value
}

// TODO(c) store the "data" of the robot and inflate the runnable rubot from the data
abstract class Robot(
    val robotId: Long, val userId: Long = COINPORT_UID, val timestamp: Long = 0) extends Serializable {

  import State._
  type Enum = Enumeration#Value
  // Option[Any] is the actual action of the robot, such as DoDepositCash.
  // This could be restrained from outter processor
  // TODO(c): try to make RobotMetrics as T
  type Action = (Option[RobotMetrics]) => (Option[Any], Enum)

  private var states = Map.empty[Enum, Action]
  private var statesPayload = Map.empty[Enum, Option[Any]]
  private var currentState: Enum = START

  addHandler(START) { _ => (None, firstState) }
  addHandler(DONE) { _ => (None, DONE) }
  inflate
  action(None)

  // invoked by outter processor
  def action(metrics: Option[RobotMetrics] = None): Option[Any] = {
    val (actualAction, to) = states(currentState)(metrics)
    transitTo(to)
    actualAction
  }

  def isDone = currentState == DONE

  protected def firstState: Enum

  protected def inflate: Unit

  protected def addHandler(state: Enum)(handler: Action) {
    require(!states.contains(state), "can't set multiple handlers to one state")
    require(state != null)
    states += (state -> handler)
  }

  protected def addPayload(state: Enum, payload: Option[Any]) {
    statesPayload += (state -> payload)
  }

  protected def getPayload[T]() =
    Option(statesPayload.getOrElse(currentState, None).getOrElse(null).asInstanceOf[T])

  protected def setPayload(payload: Option[Any]) {
    statesPayload += (currentState -> payload)
  }

  private def transitTo(state: Enum) = { currentState = state }
}
