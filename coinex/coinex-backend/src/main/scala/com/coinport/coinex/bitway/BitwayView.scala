/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import akka.event.LoggingReceive
import akka.persistence.Persistent

import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import Implicits._

class BitwayView(supportedCurrency: Currency) extends ExtendedView with BitwayManagerBehavior {
  override val processorId = BITWAY_PROCESSOR << supportedCurrency
  override val viewId = BITWAY_VIEW << supportedCurrency
  val manager = new BitwayManager(supportedCurrency)

  def receive = LoggingReceive {
    case Persistent(msg, _) => updateState(msg)
    case QueryLatestCryptoCurrencyStatus(currency, addressType) =>
      sender ! QueryLatestCryptoCurrencyStatusResult(currency, manager.getLastTxs(addressType), manager.getLastAlive)
  }
}
