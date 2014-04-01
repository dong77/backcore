/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.coinport.coinex.data._
import com.coinport.coinex.fee.rules._
import FeeRuleTypeEnum._

class WithdrawalFeeMaker(r: FeeRules) extends FeeMaker {
  val rules: WithdrawalFeeRules = r.asInstanceOf[WithdrawalFeeRules]

  override def count[AdminConfirmCashWithdrawalSuccess](
    serviceTakeItem: AdminConfirmCashWithdrawalSuccess): List[Fee] = {
    serviceTakeItem match {
      case AdminConfirmCashWithdrawalSuccess(uid, currency, amount) =>
        rules.items.getOrElse(currency, null) match {
          case null => Nil
          case item =>
            val feeAmount: Long = item match {
              case FeeRuleItem(PERCENTAGE, percentage, _) =>
                (amount * percentage).toLong
              case FeeRuleItem(CONST_PAY, _, tip) =>
                tip
            }
            List(Fee(uid, None, currency, feeAmount))
        }
      case _ => Nil
    }
  }
}
