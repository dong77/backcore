package com.coinport.coinex.common.support

import akka.actor.Actor
import akka.actor.Cancellable
import scala.concurrent.duration._
import com.coinport.coinex.data.TakeSnapshotNow
import akka.actor.ActorLogging

trait SnapshotSupport extends Actor with ActorLogging {
  implicit val executeContext = context.system.dispatcher
  private var cancellable: Cancellable = null

  abstract override def preStart() = {
    super.preStart()
    val delayinSeconds = timeInSecondsToNextHour
    val futureDelayinSeconds = 3600
    scheduleSnapshot(delayinSeconds, TakeSnapshotNow("auto", Some(futureDelayinSeconds)))
    log.info(s"the first snapshot will be taken in ${delayinSeconds / 60} minutes, then every ${futureDelayinSeconds / 60} minutes")
  }

  def takeSnapshot(cmd: TakeSnapshotNow)(action: => Unit) = {
    cancelSnapshotSchedule()
    action

    if (cmd.nextSnapshotinSeconds.isDefined && cmd.nextSnapshotinSeconds.get > 0) {
      scheduleSnapshot(cmd.nextSnapshotinSeconds.get, cmd)
      log.info(s"a new snapshot was taken, next snapshot will be taken in ${cmd.nextSnapshotinSeconds.get / 60} minutes")
    } else {
      log.info("a new snapshot was taken, no snapshot is scheduled")
    }
  }

  protected def cancelSnapshotSchedule() =
    if (cancellable != null && !cancellable.isCancelled) cancellable.cancel()

  protected def scheduleSnapshot(delayinSeconds: Int, cmd: TakeSnapshotNow) =
    cancellable = context.system.scheduler.scheduleOnce(delayinSeconds seconds, self, cmd)

  private def timeInSecondsToNextHour() = {
    val time = System.currentTimeMillis()
    (((time / 3600000 + 1) * 3600000 - time) / 1000).toInt
  }
}
