/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

import com.coinport.coinex.common._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.fee.rules._
import com.coinport.coinex.fee.rules.FeeRuleTypeEnum._
import Constants._
import Implicits._

Map(
  TRANSACTION -> new TransactionFeeRules(
    Map((Btc ~> Rmb) -> FeeRuleItem(PERCENTAGE, 0.001),
      (Rmb ~> Btc) -> FeeRuleItem(PERCENTAGE, 0.001)),
    Map(TRAILING_STOP_ORDER_ROBOT_TYPE -> FeeRuleItem(PERCENTAGE, 0.003))
  ),

  WITHDRAWAL -> new WithdrawalFeeRules(
    Map(Btc -> FeeRuleItem(CONST_PAY, amount = 1), Rmb -> FeeRuleItem(PERCENTAGE, 0.002))
  )
)
