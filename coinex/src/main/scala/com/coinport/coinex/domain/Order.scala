/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.domain

case class Order(userId: Long, id: Long, quantity: Long, price: Option[Double]) {
  def inversePrice = price match {
    case Some(p) if p > 0 => copy(price = Some(1 / p))
    case _ => this
  }
}