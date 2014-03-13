/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.userlogs
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.StateManager

class UserLogsProcessor extends ExtendedProcessor {
  override def processorId = "coinex_ulp"
  val manager = new UserLogsStateManager

  def receiveMessage: Receive = {
    case DebugDump => log.info("state: {}", manager())

    case x @ OrderCancelled(side, order) =>
      log.info("------------: " + x)
    case y @ QueryMarket(side, depth) =>
      log.info("------------: " + y)
  }
}

class UserLogsStateManager extends StateManager[UserLogs] {
  initWithDefaultState(UserLogs())

  def markOrderAs(status: OrderStatus) = {
  }
}