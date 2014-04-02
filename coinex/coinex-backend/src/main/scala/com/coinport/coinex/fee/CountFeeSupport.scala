/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.twitter.util.Eval
import java.io.File
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import com.typesafe.config.ConfigFactory

trait CountFeeSupport {
  protected val feeConfig: FeeConfig
  private lazy val feeCounter = new FeeCounter(feeConfig)

  protected def countFee(event: Any) = event match {
    case m @ OrderSubmitted(_, txs) => m.copy(txs = txs.map(tx => tx.copy(fees = Some(feeCounter.count(tx)))))
    case m: AdminConfirmCashWithdrawalSuccess => m.copy(fees = Some(feeCounter.count(m)))
    case m => m
  }
}
