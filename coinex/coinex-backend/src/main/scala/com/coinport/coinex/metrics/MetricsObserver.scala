/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.metrics

import com.coinport.coinex.common.Constants._
import com.coinport.coinex.data._
import Direction._

class MetricsObserver(
    side: MarketSide,
    transactionQueue: WindowQueue[MarketEvent] = new WindowQueue[MarketEvent](_24_HOURS, _10_SECONDS),
    minMaintainer: StackQueue[Double] = new StackQueue[Double](ascending),
    maxMaintainer: StackQueue[Double] = new StackQueue[Double](descending),
    var price: Option[Double] = None,
    var lastPrice: Option[Double] = None,
    var preRangePrice: Option[Double] = None,
    var volumeMaintainer: Option[Long] = Some(0)) {

  def pushEvent(event: MarketEvent, tick: Long) {
    transactionQueue.addAtTick(event, tick) match {
      case null => None
      case events =>
        events foreach { e =>
          e match {
            case (Some(p), Some(v)) =>
              minMaintainer.dequeue(p)
              maxMaintainer.dequeue(p)
              volumeMaintainer = volumeMaintainer map (_ - v)
              preRangePrice = Some(p)
            case _ => None
          }
        }
        event match {
          case (Some(p), Some(v)) =>
            minMaintainer.push(p)
            maxMaintainer.push(p)
            lastPrice = price
            price = Some(p)
            volumeMaintainer = volumeMaintainer map (_ + v)
          case _ => None
        }
    }
  }

  def getMetrics(tick: Long): MetricsByMarket = {
    pushEvent(null, tick)
    val gain: Option[Double] = (preRangePrice, price) match {
      case (Some(p24p), Some(p)) => Some((p - p24p) / p24p)
      case _ => None
    }
    val direction = (lastPrice, price) match {
      case (Some(lp), Some(p)) => if (lp > p) Down else if (lp < p) Up else Keep
      case _ => Keep
    }
    MetricsByMarket(
      side, price.getOrElse(0.0), minMaintainer.front, maxMaintainer.front, volumeMaintainer, gain, direction)
  }

  def copy = new MetricsObserver(side, transactionQueue.copy, minMaintainer.copy, maxMaintainer.copy,
    price, lastPrice, preRangePrice, volumeMaintainer)

  override def toString() = """side: %s; transactionQueue: %s; minMaintainer: %s; maxMaintainer: %s;
    | price: %s; lastPrice: %s; preRangePrice: %s; volumeMaintainer: %s""".stripMargin.format(
    side, transactionQueue, minMaintainer, maxMaintainer, price, lastPrice, preRangePrice, volumeMaintainer)
}
