package com.coinport.coinex.common.support

import akka.actor.Actor
import akka.persistence.Channel
import akka.persistence.ConfirmablePersistent

trait ChannelSupport { self: Actor =>
  def processorId: String

  protected def createChannelTo(dest: String) = {
    val channelName = processorId + "_2_" + dest
    context.actorOf(Channel.props(channelName), channelName)
  }

  def confirm(p: ConfirmablePersistent): Unit
}
