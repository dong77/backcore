/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.users

import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor._
import akka.persistence._
import com.coinport.coinex.common.ExtendedProcessor

class UserProcessor extends ExtendedProcessor {
  override val processorId = "coinex_up"

  val manager = new UserManager()

  def receive: Receive = {
    case x =>
  }
}
