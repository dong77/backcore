package com.coinport.coinex.robot

import com.coinport.coinex.common.Constants._

case class RobotBrain(brainId: String,
  brain: Map[String, Action] = Map.empty[String, Action])