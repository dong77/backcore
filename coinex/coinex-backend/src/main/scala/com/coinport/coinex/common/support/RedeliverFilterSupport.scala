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
  def chooseFilter: PartialFunction[Any, String]

  protected def handleUnseen: Actor.Receive

  def checkSeen: Actor.Receive = {
    case p @ ConfirmablePersistent(r, seq, _) =>
      log.debug("manager: " + manager.getClass + manager.getSnapshot)
      val isSeen = manager.seen(if (!chooseFilter.isDefinedAt(r)) "default" else chooseFilter(r), seq)
      if (isSeen) {
        log.warning("has been seen the request: ", r)
      } else {
        log.debug("not seen the request")
        handleUnseen(p)
      }
  }
}
