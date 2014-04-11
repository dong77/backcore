/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.common.support

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.persistence.ConfirmablePersistent
import akka.persistence.Processor
import com.twitter.scrooge.ThriftStruct

import com.coinport.coinex.common.AbstractManager

trait RedeliverFilterSupport[T <: ThriftStruct, M <: AbstractManager[T]] extends Processor with ActorLogging {
  val manager: M
  val channelMap: Map[Class[_], String]

  manager.initFilters(if (channelMap.isEmpty) List("all") else channelMap.values.toList)

  def checkSeen: Actor.Receive = {
    case p @ ConfirmablePersistent(r, seq, _) =>
      val isSeen = if (channelMap.isEmpty) manager.seen("all", seq) else manager.seen(channelMap(r.getClass), seq)
      if (isSeen) {
        log.warning("has been seen the request: ", r)
      } else {
        super.receive(p)
      }
  }

  abstract override def receive = checkSeen orElse super.receive
}
