/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

import com.coinport.coinex.accounts._
import com.coinport.coinex.api.model._
import com.coinport.coinex.common._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.fee._
import Constants._
import Implicits._

AccountConfig(
  feeConfig = FeeConfig(
    marketFeeRules = Map(
      (Btc ~> Ltc) -> PercentageFee(0.000),
      (Ltc ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Doge) -> PercentageFee(0.000),
      (Doge ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Pts) -> PercentageFee(0.000),
      (Pts ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Drk) -> PercentageFee(0.000),
      (Drk ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Bc) -> PercentageFee(0.000),
      (Bc ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Vrc) -> PercentageFee(0.000),
      (Vrc ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Zet) -> PercentageFee(0.000),
      (Zet ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Btsx) -> PercentageFee(0.000),
      (Btsx ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Nxt) -> PercentageFee(0.000),
      (Nxt ~> Btc) -> PercentageFee(0.000),

      (Btc ~> Xrp) -> PercentageFee(0.000),
      (Xrp ~> Btc) -> PercentageFee(0.000),

      (Cny ~> Gooc) -> PercentageFee(0.001),
      (Gooc ~> Cny) -> PercentageFee(0.001)
    ),

    robotFeeRules = Map(
      TRAILING_STOP_ORDER_ROBOT_TYPE -> PercentageFee(0.003),
      STOP_ORDER_ROBOT_TYPE -> PercentageFee(0.002)),

    transferFeeRules = Map(
      Btc -> ConstantFee(0.0005.internalValue(Btc)),
      Ltc -> ConstantFee(0.0005.internalValue(Ltc)),
      Doge -> ConstantFee(2.internalValue(Doge)),
      Pts -> ConstantFee(0.0005.internalValue(Pts)),
      Drk -> ConstantFee(0.0005.internalValue(Drk)),
      Bc -> ConstantFee(0.0005.internalValue(Bc)),
      Vrc -> ConstantFee(0.0005.internalValue(Vrc)),
      Zet -> ConstantFee(0.0005.internalValue(Zet)),
      Btsx -> ConstantFee(2.internalValue(Btsx)),
      Nxt -> ConstantFee(2.internalValue(Nxt)),
      Xrp -> ConstantFee(1.internalValue(Xrp)),
      Cny -> LadderFee(500.internalValue(Cny), 2.internalValue(Cny), 0.004)),

    freeOfTxChargeUserIdThreshold = 1E9.toLong + 1000 // first 1000 users
    )
)
