/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import scala.collection.immutable.SortedSet
import MarketState._

case class MarketSide(outCurrency: Currency, inCurrency: Currency)  {
  def reverse = inCurrency ~> outCurrency
  override def toString = "%s_%s".format(outCurrency, inCurrency).toLowerCase
}