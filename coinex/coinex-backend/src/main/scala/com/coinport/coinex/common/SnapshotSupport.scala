package com.coinport.coinex.common

import akka.actor.Actor
import akka.actor.Cancellable
import akka.util.Timeout
import scala.concurrent.duration._
import com.coinport.coinex.data.TakeSnapshotNow

private[common] trait SnapshotSupport extends Actor {
  implicit val executeContext = context.system.dispatcher
  private var cancellable: Cancellable = null

  abstract override def preStart() = {
    super.preStart()
    scheduleSnapshot(30, TakeSnapshotNow("auto", Some(30)))
  }

  def takeSnapshot(cmd: TakeSnapshotNow)(action: => Unit) = {
    cancelSnapshotSchedule()
    action
    if (cmd.nextSnapshotInMinutes.isDefined && cmd.nextSnapshotInMinutes.get > 0) {
      scheduleSnapshot(cmd.nextSnapshotInMinutes.get, cmd)
    }
  }

  protected def cancelSnapshotSchedule() =
    if (cancellable != null && !cancellable.isCancelled) cancellable.cancel()

  protected def scheduleSnapshot(delayInMinutes: Int, cmd: TakeSnapshotNow) =
    cancellable = context.system.scheduler.scheduleOnce(delayInMinutes minutes, self, cmd)
}