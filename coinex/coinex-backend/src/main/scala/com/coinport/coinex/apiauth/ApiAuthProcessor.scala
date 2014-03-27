
/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */
package com.coinport.coinex.apiauth

import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor._
import akka.persistence._
import com.coinport.coinex.common.ExtendedProcessor
import RegisterationFailureReason._
import akka.event.LoggingReceive

class ApiAuthProcessor extends ExtendedProcessor {
  override val processorId = "coinex_aap"

  def receive = LoggingReceive {
    // ------------------------------------------------------------------------------------------------
    // case TakeSnapshotNow => saveSnapshot(manager())

    case SaveSnapshotSuccess(metadata) =>

    case SaveSnapshotFailure(metadata, reason) =>

    case SnapshotOffer(meta, snapshot) =>
      log.info("Loaded snapshot {}", meta)
    // manager.reset(snapshot.asInstanceOf[UserState])

    case DebugDump =>
    // log.info("state: {}", manager())

    case QueryActorStats =>
    //  sender ! manager()

    // ------------------------------------------------------------------------------------------------
    // Commands
    case p @ Persistent(DoAddNewApiSecret, _) =>
    case p @ Persistent(DoDeleteApiSecret, _) =>

    case p @ Persistent(something, _) =>
      throw new IllegalArgumentException(s"ApiAuthProcessor doesn't handle event: Persistent($something.getClass.getSimpleName)")
  }
}
