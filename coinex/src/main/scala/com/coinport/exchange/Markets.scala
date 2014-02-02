package com.coinport.exchange

object CurrencyType extends Enumeration {
  type CurrencyType = Value
  val RMB, BTC = Value
}
import CurrencyType._

sealed abstract class Market(val mainCurrency: CurrencyType, val secondaryCurrency: CurrencyType) {
  override def toString = mainCurrency.toString().toLowerCase() + "_" + secondaryCurrency.toString().toLowerCase()
}

case object BtcRmbMarket extends Market(BTC, RMB)
