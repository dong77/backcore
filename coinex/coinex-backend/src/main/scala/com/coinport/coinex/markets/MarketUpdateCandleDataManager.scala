/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.StateManager
import Implicits._

class MarketCandleDataManager extends StateManager[CandleDataState] {
  initWithDefaultState(CandleDataState())

  val minute = 60 * 1000
  val quarter = 15 * minute
  val hour = 4 * quarter
  val day = 24 * hour
}