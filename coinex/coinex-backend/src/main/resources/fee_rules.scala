/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

import com.coinport.coinex.common._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.fee._
import Constants._
import Implicits._

FeeConfig(
  marketFeeRules = Map(
    (Pts ~> Rmb) -> PercentageFee(0.003),
    (Rmb ~> Pts) -> PercentageFee(0.003)),

  robotFeeRules = Map(
    TRAILING_STOP_ORDER_ROBOT_TYPE -> PercentageFee(0.003),
    STOP_ORDER_ROBOT_TYPE -> ConstantFee(10)),

  withdrawalFeeRules = Map(
    Btc -> ConstantFee(1),
    Rmb -> PercentageFee(0.002)))
