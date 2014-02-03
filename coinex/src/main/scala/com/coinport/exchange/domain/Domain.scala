package com.coinport.exchange.domain

// Currencies ------------
sealed trait Currency { def id: Int }

sealed trait FiatCurrency extends Currency
case object RMB extends FiatCurrency { val id = 1 }
case object USD extends FiatCurrency { val id = 2 }

sealed trait VirtualCurrency extends Currency
case object BTC extends VirtualCurrency { val id = 1001 }
case object LTC extends VirtualCurrency { val id = 1002 }

// Accounts ------------
sealed trait AccountType { def id: Int }
case object Cash extends AccountType { val id = 1 }
case object Margin extends AccountType { val id = 2 }

case class AccountIdentifier(uid: Long, currency: VirtualCurrency, accountType: AccountType = Cash) {
  override def toString = "account:U%d/C%d/T%d".format(uid, currency.id, accountType.id)
}

sealed trait Account

case class VirtAccount(id: AccountIdentifier, available: Double = 0, withheld: Double = 0) {
  def total = available + withheld
}

case class FiatAccount(id: AccountIdentifier, available: Double = 0, withheld: Double = 0) {
  def total = available + withheld
}

// Deposits ------------

// TODO: resolve double deposit/withdrawal
case class Deposit(id: Long = -1, accountId: AccountIdentifier, amount: Double, created: Long = System.currentTimeMillis)
case class Withdrawal(id: Long = -1, accountId: AccountIdentifier, amount: Double, created: Long = System.currentTimeMillis)
