/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee
import com.coinport.coinex.data.MarketSide
import com.coinport.coinex.data.Currency

final case class FeeConfig(
  marketFeeRules: Map[MarketSide, FeeRule],
  robotFeeRules: Map[Int, FeeRule],
  transferFeeRules: Map[Currency, FeeRule])