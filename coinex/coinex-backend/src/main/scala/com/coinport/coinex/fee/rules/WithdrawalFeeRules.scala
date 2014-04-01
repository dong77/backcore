/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee.rules

import com.coinport.coinex.data.Currency

class WithdrawalFeeRules(val items: Map[Currency, FeeRuleItem]) extends FeeRules {
}
