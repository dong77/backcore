package com.coinport.coinex.transfer

import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Currency

class AccountTransferConfig(
  val transferDebug: Boolean = true,
  val confirmNumMap: collection.Map[Currency, Int] = collection.Map(Btc -> 1, Ltc -> 4, Doge -> 4),
  val manualCurrency: collection.Set[Currency] = collection.Set.empty[Currency],
  val succeededRetainNum: collection.Map[Currency, Int] = collection.Map(Btc -> 100, Ltc -> 200, Doge -> 300),
  val enableAutoConfirm: Boolean = true,
  val autoConfirmAmount: collection.Map[Currency, Long] = collection.Map.empty[Currency, Long])

