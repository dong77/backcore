/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.common.support

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.persistence.ConfirmablePersistent

import com.coinport.coinex.common.AbstractManager

trait RedeliverFilterSupport[T <: AnyRef, M <: AbstractManager[T]] extends Actor with ActorLogging {
  val manager: M
  val channelMap: Map[Class[_], String]

  protected def handleUnseen: Actor.Receive

  manager.initFilters(if (channelMap.isEmpty) List("all") else channelMap.values.toList)

  def checkSeen: Actor.Receive = {
    case p @ ConfirmablePersistent(r, seq, _) =>
      val isSeen = if (channelMap.isEmpty) manager.seen("all", seq) else manager.seen(channelMap(r.getClass), seq)
      if (isSeen) {
        log.warning("has been seen the request: ", r)
      } else {
        handleUnseen(p)
      }
  }
}
