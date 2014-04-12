/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.metrics

import com.coinport.coinex.common.Manager
import com.coinport.coinex.data._
import Implicits._

class MetricsManager extends Manager[MetricsState] {

  var state = MetricsState()

  override def getSnapshot = state

  override def loadSnapshot(s: MetricsState) {
    state = s
  }

  def update(side: MarketSide, price: Double, volume: Long, reverseVolume: Long, tick: Long) {
    state = state.pushEvent(side, (Some(price), Some(volume)), tick)
    state = state.pushEvent(side.reverse, (Some(1 / price), Some(reverseVolume)), tick)
  }

  def getMetrics(tick: Long): Metrics = {
    state.getMetrics(tick)
  }

}
