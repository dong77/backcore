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
        Cny -> CashAccount(Currency.Cny, 100000, 0, 0)
      )
      val backendAccount = com.coinport.coinex.data.UserAccount(123L, cashAccounts = accounts)
      val userAccount: com.coinport.coinex.api.model.UserAccount = backendAccount

      userAccount mustEqual com.coinport.coinex.api.model.UserAccount(
        "123",
        Map(
          "BTC" -> AccountItem(CurrencyObject(Btc, 8000), CurrencyObject(Btc, 2000), CurrencyObject(Btc, 0)),
          "CNY" -> AccountItem(CurrencyObject(Cny, 100000), CurrencyObject(Cny, 0), CurrencyObject(Cny, 0))
        )
      )
    }
  }
}