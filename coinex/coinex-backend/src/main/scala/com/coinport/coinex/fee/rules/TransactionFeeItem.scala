/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee.rules

import FeeRuleTypeEnum._

class TransactionFeeItem {
  var kind: FeeRuleType = PERCENTAGE
  var percentage: Double = 0.0
  var amount: Long = 0
}
