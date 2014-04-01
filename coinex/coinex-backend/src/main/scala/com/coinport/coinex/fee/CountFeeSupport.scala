/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.twitter.util.Eval
import java.io.File

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import com.coinport.coinex.fee.rules.FeeRules
import com.typesafe.config.ConfigFactory

trait CountFeeSupport {
  // TODO(c): pass from outside
  private val feeRulesMap = getFeeRules(ConfigFactory.load().getString("akka.exchange.fee-rules-path"))

  val feeMakers = Map(
    TRANSACTION -> new TransactionFeeMaker(feeRulesMap(TRANSACTION)),
    WITHDRAWAL -> new WithdrawalFeeMaker(feeRulesMap(WITHDRAWAL))
  )

  private def getFeeRules(configPath: String): Map[String, FeeRules] = {
    val fullPath = classOf[CountFeeSupport].getClassLoader().getResource(configPath).getPath()
    (new Eval()(new File(fullPath))).asInstanceOf[Map[String, FeeRules]]
  }

  protected def countFee[T](event: T) = event match {
    case m @ OrderSubmitted(originOrderInfo, txs) =>
      val txsWithFee = txs map { tx =>
        tx.copy(fees = Some(feeMakers(TRANSACTION).count(tx)))
      }
      m.copy(txs = txsWithFee)
    case m: AdminConfirmCashWithdrawalSuccess =>
      m.copy(fees = Some(feeMakers(WITHDRAWAL).count(m)))
    case m => m
  }
}
