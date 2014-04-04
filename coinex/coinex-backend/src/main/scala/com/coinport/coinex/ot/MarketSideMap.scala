package com.coinport.coinex.ot

import com.coinport.coinex.data.{ Currency, MarketSide }
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits._

object MarketSideMap {
  private val multiple = 12345

  private val sideMap: scala.collection.immutable.Map[MarketSide, Long] =
    Currency.list.map(c =>
      Currency.list.map(c2 =>
        (c ~> c2 -> (c.value.toLong * multiple).+(c2.value.toLong))
      ).toMap[MarketSide, Long]
    ).flatten.toMap

  private val reverseSideMap = sideMap.map(x => x._2 -> x._1)

  def getSide(key: Long) = reverseSideMap.get(key).get
  def getValue(key: MarketSide) = sideMap.get(key).get
}
