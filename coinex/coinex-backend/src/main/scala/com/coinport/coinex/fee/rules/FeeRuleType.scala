/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee.rules

object FeeRuleTypeEnum extends Enumeration {
  type FeeRuleType = Value
  val PERCENTAGE, CONST_PAY = Value
}
