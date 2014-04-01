/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import akka.event.LoggingReceive
import akka.persistence._
import com.coinport.coinex.data._
import com.coinport.coinex.fee._
import com.coinport.coinex.fee.rules.FeeRules
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.common.ExtendedProcessor

trait CountFeeProcessor extends ExtendedProcessor {

  val configMap: Map[String, FeeRules]

  val feeMakers = Map(
    TRANSACTION -> new TransactionFeeMaker(configMap(TRANSACTION)),
    WITHDRAWAL -> new WithdrawalFeeMaker(configMap(WITHDRAWAL))
  )

  type FeeReceive = PartialFunction[(Any, List[Fee]), Unit]

  abstract override def receive = LoggingReceive {
    case p @ ConfirmablePersistent(OrderSubmitted(originOrderInfo, txs), seq, _) =>
      txs map { tx =>
      }
  }

  private def tryToCountFee[T](feeMaker: FeeMaker, m: T) = {
    if (feeMaker != null) {
      val fees = feeMaker.count(m)
      if (fees != Nil)
        handleFee(fees)
    }
    super.receive(m)
  }

  def handleFee(fees: List[Fee])
}
