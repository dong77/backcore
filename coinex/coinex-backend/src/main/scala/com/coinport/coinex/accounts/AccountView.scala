/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.data._
import Implicits._
import com.coinport.coinex.fee.CountFeeSupport
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.fee.FeeConfig

class AccountView(val feeConfig: FeeConfig) extends ExtendedView with AccountManagerBehavior {
  override val processorId = ACCOUNT_PROCESSOR <<
  override val viewId = ACCOUNT_VIEW<<
  val manager = new AccountManager()

  def receive = LoggingReceive {
    case Persistent(msg, _) => updateState(msg)
    case QueryAccount(-1L) => sender ! QueryAccountResult(manager.aggregation)
    case QueryAccount(userId) => sender ! QueryAccountResult(manager.getUserAccounts(userId))
  }
}
