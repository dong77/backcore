package com.coinport.coinex.data

import Implicits._

object Market {
  def fromString(str: String) = {
    try {
      Market(Currency.valueOf(str.substring(0, 2).toLowerCase.capitalize).get, Currency.valueOf(str.substring(3, 5).toLowerCase.capitalize).get)
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }
}

case class Market(cur1: Currency, cur2: Currency) {
  private val multiple = 12345

  val (cur_high, cur_low, direction) = if (cur1.getValue() > cur2.getValue()) (cur1, cur2, true) else (cur2, cur1, false)
  def getMarketSide(d: Boolean) = if (d) cur_high ~> cur_low else cur_low ~> cur_high
  def getLongValue = (cur_high.value.toLong * multiple).+(cur_low.value.toLong)

  override def toString = (cur_high.toString + cur_low.toString).toUpperCase
}

object MarketMap {
  val marketMap: scala.collection.immutable.Map[Market, Long] =
    Currency.list.map(c =>
      Currency.list.map { c2 =>
        val market = Market(c, c2)
        (market -> market.getLongValue)

      }.toMap[Market, Long]
    ).flatten.toMap

  private val reverseMarketMap = marketMap.map(x => x._2 -> x._1)

  def getSide(key: Long) = reverseMarketMap.get(key).get
  def getValue(key: Market) = marketMap.get(key).get
}
