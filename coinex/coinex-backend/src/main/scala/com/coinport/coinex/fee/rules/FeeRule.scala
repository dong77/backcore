/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee.rules

case class FeeRule(kind: FeeRuleType = PercentageFee, percentage: Double = 0.0, amount: Long = 0)
