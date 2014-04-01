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
import Implicits._

class TransactionFeeMakerSpec extends Specification {
  val fullPath = classOf[TransactionFeeMakerSpec].getClassLoader().getResource("fee_rules.scala").getPath()
  val feeRulesMap = (new Eval()(new File(fullPath))).asInstanceOf[Map[String, FeeRules]]

  val feeMaker = new TransactionFeeMaker(feeRulesMap(TRANSACTION))

  "transaction fee maker" should {
    val takerSide = Btc ~> Rmb
    val taker = Order(userId = 5, id = 5, price = Some(2000), quantity = 100000, timestamp = Some(0))
    val maker = Order(userId = 3, id = 3, price = Some(1.0 / 5000), quantity = 10000000, timestamp = Some(0))
    val updatedMaker = maker.copy(quantity = 0) // buy 2

    "transaction btc-rmb with 0.1% fee" in {
      val transaction = Transaction(50000, 0, takerSide, taker --> taker.copy(quantity = 98000), maker --> updatedMaker)
      val fees = feeMaker.count(transaction)
      fees mustEqual List(Fee(3, None, Btc, 2, None), Fee(5, None, Rmb, 10000, None))
      1 mustEqual 1
    }

    "transaction btc-pts with no fee" in {
      val transaction = Transaction(1, 1, (Btc ~> Pts), taker --> taker.copy(quantity = 98000), maker --> updatedMaker)
      val fees = feeMaker.count(transaction)
      fees mustEqual List.empty[Fee]
      1 mustEqual 1
    }

    "transaction btc-rmb with robot fee" in {
      val robotTaker = taker.copy(robotType = Some(STOP_ORDER_ROBOT_TYPE))
      val transaction = Transaction(1, 1, takerSide, robotTaker --> robotTaker.copy(quantity = 98000),
        maker.copy(robotType = Some(3)) --> updatedMaker.copy(robotType = Some(3)))
      val fees = feeMaker.count(transaction)
      fees mustEqual List(Fee(3, None, Btc, 2, None), Fee(5, None, Rmb, 10010, None))
    }

    "transaction btc-rmb with robot fee" in {
      val robotTaker = taker.copy(robotType = Some(STOP_ORDER_ROBOT_TYPE))
      val transaction = Transaction(1, 1, takerSide, robotTaker --> robotTaker.copy(quantity = 98000),
        maker.copy(robotType = Some(TRAILING_STOP_ORDER_ROBOT_TYPE)) -->
          updatedMaker.copy(robotType = Some(TRAILING_STOP_ORDER_ROBOT_TYPE)))
      val fees = feeMaker.count(transaction)
      fees mustEqual List(Fee(3, None, Btc, 8, None), Fee(5, None, Rmb, 10010, None))
    }
  }
}
