/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.twitter.util.Eval
import java.io.File
import org.specs2.mutable._

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.fee.rules.FeeRules

class WithdrawalFeeMakerSpec extends Specification {
  val fullPath = classOf[WithdrawalFeeMakerSpec].getClassLoader().getResource("fee_rules.scala").getPath()
  val feeRulesMap = (new Eval()(new File(fullPath))).asInstanceOf[Map[String, FeeRules]]

  val feeMaker = new WithdrawalFeeMaker(feeRulesMap(WITHDRAWAL))

  "withdrawal fee maker" should {
    "withdrawal rmb with 0.2% fee" in {
      val withdrawalRequest = AdminConfirmCashWithdrawalSuccess(1, Rmb, 12000)
      val fees = feeMaker.count(withdrawalRequest)
      fees mustEqual List(Fee(1, None, Rmb, 24, None))
    }

    "withdrawal btc with 1 fee" in {
      var withdrawalRequest = AdminConfirmCashWithdrawalSuccess(1, Btc, 12000)
      var fees = feeMaker.count(withdrawalRequest)
      fees mustEqual List(Fee(1, None, Btc, 1, None))
      withdrawalRequest = AdminConfirmCashWithdrawalSuccess(1, Btc, 22000)
      fees = feeMaker.count(withdrawalRequest)
      fees mustEqual List(Fee(1, None, Btc, 1, None))
    }

    "withdrawal pts with no result" in {
      var withdrawalRequest = AdminConfirmCashWithdrawalSuccess(1, Pts, 12000)
      var fees = feeMaker.count(withdrawalRequest)
      fees mustEqual List.empty[Fee]
    }
  }
}
