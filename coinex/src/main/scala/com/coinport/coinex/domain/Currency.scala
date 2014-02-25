package com.coinport.coinex.domain

// Currency ------------------------
sealed trait Currency
sealed trait FiatCurrency extends Currency
sealed trait EncryptedCurrency extends Currency

case object RMB extends FiatCurrency
case object USD extends FiatCurrency

case object BTC extends EncryptedCurrency
case object LTC extends EncryptedCurrency
case object PTS extends EncryptedCurrency
case object BTS extends EncryptedCurrency