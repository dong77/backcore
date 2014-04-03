/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.data

import com.coinport.coinex.data._
import com.coinport.coinex.metrics._

object MetricsState {
}

// MetricsObserver is mutable and need clone when snapshoting
import MetricsState._
case class MetricsState(
    observers: Map[MarketSide, MetricsObserver] = Map.empty[MarketSide, MetricsObserver]) {

  def snapshot: MetricsState = {
    val newObservers = observers map (item => (item._1 -> item._2.copy))
    copy(observers = newObservers)
  }
}
