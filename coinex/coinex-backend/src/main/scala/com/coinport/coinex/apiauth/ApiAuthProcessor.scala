
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
import com.coinport.coinex.common.StateManager
import com.coinport.coinex.util.Hash
import com.google.common.io.BaseEncoding
import ErrorCode._
import akka.event.LoggingReceive

class ApiAuthProcessor(seed: String) extends ExtendedProcessor {
  override val processorId = "coinex_aap"

  val manager = new ApiAuthManager(seed)

  def receive = LoggingReceive {
    case p @ Persistent(DoAddNewApiSecret(userId), _) =>
      manager.addNewSecret(userId) match {
        case Left(code) => sender ! ApiSecretOperationResult(code, manager.getUserSecrets(userId))
        case Right(_) => sender ! ApiSecretOperationResult(ErrorCode.Ok, manager.getUserSecrets(userId))
      }

    case p @ Persistent(DoDeleteApiSecret(secret), _) =>
      manager.deleteSecret(secret) match {
        case Left(code) => sender ! ApiSecretOperationResult(code, Nil)
        case Right(_) => sender ! ApiSecretOperationResult(ErrorCode.Ok, Nil)
      }
  }
}
