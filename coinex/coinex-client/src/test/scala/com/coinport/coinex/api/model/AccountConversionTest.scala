/**
 * Copyright (C) 2014 Coinport Inc.
 */
package com.coinport.coinex.api.model

import org.specs2.mutable._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._

class AccountConversionTest extends Specification {
  "account conversions" should {
    "user account conversion" in {
      val accounts: scala.collection.Map[Currency, CashAccount] = scala.collection.Map(
        Btc -> CashAccount(Currency.Btc, 8000, 2000, 0),
        Rmb -> CashAccount(Currency.Rmb, 100000, 0, 0)
      )
      val backendAccount = com.coinport.coinex.data.UserAccount(123L, cashAccounts = accounts)
      val userAccount: com.coinport.coinex.api.model.UserAccount = backendAccount

      userAccount mustEqual com.coinport.coinex.api.model.UserAccount(123L, Map("RMB" -> 1000.0, "BTC" -> 8.0))
    }
  }
}