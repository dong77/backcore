package com.coinport.coinex.ot

import com.coinport.coinex.data.MarketSide
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits._

object MarketSideMap {
  //TODO(xi) need to find a way to cover all market
  private val sideMap: Map[MarketSide, Int] = Map((Btc ~> Rmb -> 0), (Ltc ~> Rmb) -> 1, (Pts ~> Rmb) -> 2)
  private val reverseSideMap = sideMap.map(x => x._2 -> x._1)

  def getSide(key: Int) = reverseSideMap.get(key).get
  def getValue(key: MarketSide) = sideMap.get(key).get
}
