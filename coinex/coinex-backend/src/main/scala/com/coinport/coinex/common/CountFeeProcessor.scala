/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.common

import akka.event.LoggingReceive

import com.coinport.coinex.data.Fee
import com.coinport.coinex.fee.FeeMaker

trait CountFeeProcessor extends ExtendedProcessor {

  type FeeReceive = PartialFunction[(Any, List[Fee]), Unit]

  abstract override def receive = LoggingReceive {
    case e => tryToCountFee(null, e)
  }

  private def tryToCountFee[T](feeMaker: FeeMaker[T], m: T) = {
    if (feeMaker != null) {
      val (event, fees) = feeMaker.tipping(m)
      if (fees != Nil)
        handleFee(fees)
    }
    super.receive(m)
  }

  def handleFee(fees: List[Fee])
}
