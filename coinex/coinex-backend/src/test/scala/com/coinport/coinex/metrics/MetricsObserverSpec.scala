/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.metrics

import org.specs2.mutable._

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import Currency._
import Direction._
import Implicits._

class MetricsObserverSpec extends Specification {
  "MetricsObserver" should {
    "normal test" in {
      val observer = new MetricsObserver((Btc ~> Rmb), transactionQueue = new WindowQueue[MarketEvent](10, 3))
      observer.pushEvent(create(12.5, 100), 0)
      observer.getMetrics(1) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 12.5, Some(12.5), Some(12.5), Some(100), None, Keep)
      observer.pushEvent(create(2.5, 90), 0)
      observer.getMetrics(2) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 12.5, Some(12.5), Some(12.5), Some(100), None, Keep)
      observer.getMetrics(3) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 12.5, Some(12.5), Some(12.5), Some(100), None, Keep)
      observer.getMetrics(8) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 12.5, Some(12.5), Some(12.5), Some(100), None, Keep)
      observer.getMetrics(9) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 12.5, None, None, Some(0), Some(0.0), Keep)

      observer.pushEvent(create(2.5, 10), 10)
      observer.getMetrics(10) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 2.5, Some(2.5), Some(2.5), Some(10), Some(-0.8), Down)
      observer.pushEvent(create(3.5, 8), 12)
      observer.getMetrics(12) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 3.5, Some(2.5), Some(3.5), Some(18), Some(-0.72), Up)
      observer.pushEvent(create(12.5, 7), 15)
      observer.getMetrics(15) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 12.5, Some(2.5), Some(12.5), Some(25), Some(0.0), Up)
      observer.pushEvent(create(4.5, 1), 18)
      observer.getMetrics(18) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 4.5, Some(3.5), Some(12.5), Some(16), Some(0.8), Down)
      observer.pushEvent(create(0.5, 12), 35)
      observer.getMetrics(35) mustEqual MetricsByMarket(
        MarketSide(Btc, Rmb), 0.5, Some(0.5), Some(0.5), Some(12), Some(-4 / 4.5), Down)
    }
  }
  private def create(p: Double, v: Long) = (Some(p), Some(v))
}
