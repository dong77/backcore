/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee.rules

import com.coinport.coinex.data.MarketSide

class TransactionFeeRules(val marketItems: Map[MarketSide, FeeRuleItem],
    val robotItems: Map[Int, FeeRuleItem]) extends FeeRules {
}
