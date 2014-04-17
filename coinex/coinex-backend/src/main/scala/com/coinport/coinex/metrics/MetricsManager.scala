/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.metrics

import com.coinport.coinex.common.Manager
import com.coinport.coinex.data._
import Implicits._

class MetricsManager extends Manager[TMetricsState] {

  var state = MetricsState()

  override def getSnapshot = state.toThrift.copy(filters = getFiltersSnapshot)

  override def loadSnapshot(s: TMetricsState) {
    state = MetricsState(s)
    loadFiltersSnapshot(s.filters)
  }

  def update(side: MarketSide, price: Double, volume: Long, reverseVolume: Long, tick: Long) {
    state = state.pushEvent(side, (Some(price), Some(volume)), tick)
    state = state.pushEvent(side.reverse, (Some(1 / price), Some(reverseVolume)), tick)
  }

  def getMetrics: Metrics = {
    state.getMetrics
  }

}
