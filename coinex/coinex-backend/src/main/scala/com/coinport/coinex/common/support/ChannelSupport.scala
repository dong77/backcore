package com.coinport.coinex.common.support

import akka.actor.Actor
import akka.persistence.{ Channel, ChannelSettings}
import akka.persistence.ConfirmablePersistent
import scala.concurrent.duration._

trait ChannelSupport { self: Actor =>
  def processorId: String

  protected def createChannelTo(dest: String) = {
    val channelName = processorId + "_2_" + dest
    context.actorOf(Channel.props(channelName, ChannelSettings(redeliverInterval = 10 seconds)), channelName)
  }

  def confirm(p: ConfirmablePersistent): Unit
}
