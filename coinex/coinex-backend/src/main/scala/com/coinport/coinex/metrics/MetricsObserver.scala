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
    minMaintainer: StackQueue[Double] = new StackQueue[Double](ascending, (_24_HOURS / _10_SECONDS).toInt),
    maxMaintainer: StackQueue[Double] = new StackQueue[Double](descending, (_24_HOURS / _10_SECONDS).toInt),
    preMaintainer: StackQueue[Double] = new StackQueue[Double]((l, r) => true, (_24_HOURS / _10_SECONDS).toInt),
    var price: Option[Double] = None,
    var lastPrice: Option[Double] = None,
    var volumeMaintainer: Long = 0L) {

  def pushEvent(event: MarketEvent, tick: Long) {
    transactionQueue.addAtTick(event, tick) match {
      case null => None
      case events =>
        events foreach { e =>
          e match {
            case (Some(p), Some(v)) =>
              minMaintainer.dequeue(p)
              maxMaintainer.dequeue(p)
              preMaintainer.dequeue(p)
              volumeMaintainer -= v
            case _ => None
          }
        }
        event match {
          case (Some(p), Some(v)) =>
            minMaintainer.push(p)
            maxMaintainer.push(p)
            preMaintainer.push(p)
            lastPrice = price
            price = Some(p)
            volumeMaintainer += v
          case _ => None
        }
    }
  }

  def getMetrics(tick: Long): MetricsByMarket = {
    pushEvent(null, tick)
    val gain: Option[Double] = (preMaintainer.front, price) match {
      case (Some(prp), Some(p)) => Some((p - prp) / prp)
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
    preMaintainer.copy, price, lastPrice, volumeMaintainer)

  override def toString() = """side: %s; transactionQueue: %s; minMaintainer: %s; maxMaintainer: %s;
    | preMaintainer: %s; price: %s; lastPrice: %s; volumeMaintainer: %s""".stripMargin.format(
    side, transactionQueue, minMaintainer, maxMaintainer, preMaintainer, price, lastPrice, volumeMaintainer)
}
