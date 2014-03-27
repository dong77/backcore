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
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.robot.sample._

// TODO(c): need put processors path used by robot to the parameter of RobotProcessor
class RobotProcessor(routers: LocalRouters) extends ExtendedProcessor {
  override val processorId = "coinex_rp"
  val channelToMarketProcessors = createChannelTo("mps")

  // TODO(c): put activateRobotsInterval to the config file
  val activateRobotsInterval = 5 second
  private var cancellable: Cancellable = null
  private val manager = new RobotManager()
  /*
  val brain = Map(
    "START" -> """
      var r = robot.setPayload("START", Some(10))
      (r -> "STATE_A", None)
    """,

    "STATE_A" -> """
      val times = robot.getPayload[Int]("START")
      val r = robot.setPayload("START", times map { _ - 1 })
      if (times.get != 1) {
        (r -> "STATE_A", Some(0))
      } else {
        (r -> "DONE", Some(1))
      }
    """
  )
  */
  /*
  val brain = Map(
    "START" -> """
      var r = robot.setPayload("START", Some(10))
      (r -> "STATE_A", None)
    """,

    "STATE_A" -> """
      (robot -> "DONE", Some(DoSubmitOrder(MarketSide(Btc, Rmb), Order(1, 1, 2, Some(3429.0)))))
    """
  )
  var robot = new DRobot(1, 1, 1, brain)
  manager.addRobot(robot)
  */

  implicit def executionContext = context.dispatcher
  implicit val timeout: Timeout = 1 second

  override def preStart = {
    super.preStart
    // TODO(c): seems before replay start
    scheduleActivateRobots()
  }

  def receive = LoggingReceive {
    case TakeSnapshotNow => saveSnapshot(manager())

    case SaveSnapshotSuccess(metadata) =>

    case SaveSnapshotFailure(metadata, reason) =>

    case SnapshotOffer(meta, snapshot) =>
      log.info("Loaded snapshot {}", meta)
      manager.reset(snapshot.asInstanceOf[RobotState])

    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(DoUpdateMetrics(metrics), _) =>
      manager.updateMetrics(metrics)
      activateRobots()

    case ActivateRobotsNow =>
      if (recoveryFinished) {
        routers.robotMetricsView.ask(
          QueryRobotMetrics).mapTo[RobotMetrics] foreach { metrics =>
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
    println(manager())
    manager().getRobotPool.map(robot => robot.action(Some(manager().metrics))) foreach { res =>
      res match {
        case (newRobot, action) =>
          // robot doesn't change id
          manager.removeRobot(newRobot.robotId)
          if (!newRobot.isDone)
            manager.addRobot(newRobot)
          action match {
            case Some(m: DoSubmitOrder) => routers.accountProcessor forward Persistent(m)
            case None => None
            case m => log.warning("Robot can't send this message: " + m.getClass.getCanonicalName)
          }
        case _ =>
      }
    }
  }
}
