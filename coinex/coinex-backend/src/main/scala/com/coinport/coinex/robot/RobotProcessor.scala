/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.robot

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.persistence._
import akka.util.Timeout
import scala.concurrent.duration._

import com.coinport.coinex.LocalRouters
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.robot.sample._
import Implicits._

class RobotProcessor(routers: LocalRouters) extends ExtendedProcessor with Processor {
  override def processorId = ROBOT_PROCESSOR <<
  val channelToMarketProcessors = createChannelTo(MARKET_PROCESSOR <<)

  // TODO(c): put activateRobotsInterval to the config file
  val activateRobotsInterval = 5 second
  private var cancellable: Cancellable = null
  val manager = new RobotManager()

  implicit def executionContext = context.dispatcher
  implicit val timeout: Timeout = 1 second

  override def preStart = {
    super.preStart
    // TODO(c): seems before replay start
    scheduleActivateRobots()
  }

  def receive = LoggingReceive {

    case Persistent(DoUpdateMetrics(metrics), _) =>
      manager.updateMetrics(metrics)
      activateRobots()

    case ActivateRobotsNow =>
      if (recoveryFinished) {
        routers.metricsView.ask(
          QueryMetrics).mapTo[Metrics] foreach { metrics =>
            self ! Persistent(DoUpdateMetrics(metrics))
          }
      }

    case Persistent(DoSubmitRobot(robot), _) =>
      manager.addRobot(robot)

    case Persistent(DoCancelRobot(id), _) =>
      manager.removeRobot(id)
  }

  private def scheduleActivateRobots() = {
    cancellable = context.system.scheduler.schedule(
      activateRobotsInterval, activateRobotsInterval, self, ActivateRobotsNow)
  }

  private def cancelActivateRobot() =
    if (cancellable != null && !cancellable.isCancelled) cancellable.cancel()

  private def activateRobots() {
    manager().getRobotPool.map(robot => robot.action(Some(manager().metrics))) foreach { res =>
      res match {
        case (newRobot, action) =>
          // robot doesn't change id
          manager.removeRobot(newRobot.robotId)
          if (!newRobot.isDone)
            manager.addRobot(newRobot)
          if (recoveryFinished) {
            action match {
              case Some(m: DoSubmitOrder) =>
                log.debug("robot send the request: " + m);
                routers.accountProcessor forward m
              case None => None
              case m => log.warning("Robot can't send this message: " + m.getClass.getCanonicalName)
            }
          }
        case _ =>
      }
    }
  }
}
