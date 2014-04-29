/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway_client

import scala.collection.mutable.Map
import scala.collection.mutable.Set

import com.coinport.coinex.common.Manager
import com.coinport.coinex.data._
import Currency._

class BitwayManager extends Manager[TBitwayState] {

  val unusedAddresses = Map.empty[Currency, Set[Address]]
  val usedAddresses = Map.empty[Currency, Set[Address]]
  val cursors = Map.empty[Currency, String]
  val supportedCurrency = Set[Currency](Btc) // TODO(c): put this to config file

  val FAUCET_THRESHOLD: Double = 0.5
  val INIT_ADDRESS_NUM: Int = 100

  def getSnapshot = TBitwayState(cursors.map(kv =>
    (kv._1 -> CurrencyNetwork(
      kv._1, kv._2,
      unusedAddresses(kv._1).clone,
      usedAddresses(kv._1).clone)
    )));

  def loadSnapshot(s: TBitwayState) {
    unusedAddresses.clear
    unusedAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[Address] ++ kv._2.unusedAddresses)))
    usedAddresses.clear
    usedAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[Address] ++ kv._2.usedAddresses)))
    cursors.clear
    cursors ++= s.stats.map(kv => (kv._1 -> kv._2.cursor))
    supportedCurrency.clear
    supportedCurrency ++= s.stats.map(kv => kv._1)
  }

  def isDryUp(currency: Currency) = (unusedAddresses.getOrElseUpdate(currency, Set.empty[Address]).size == 0 ||
    usedAddresses.getOrElseUpdate(currency, Set.empty[Address]).size > unusedAddresses.getOrElseUpdate(
      currency, Set.empty[Address]).size * FAUCET_THRESHOLD)

  def allocateAddress(currency: Currency): (Option[Address], Boolean) = {
    val addresses = unusedAddresses.getOrElseUpdate(currency, Set.empty[Address])
    if (addresses.isEmpty) {
      (None, true)
    } else {
      val validAddress = addresses.head
      addresses.remove(validAddress)
      usedAddresses.getOrElseUpdate(currency, Set.empty[Address]).add(validAddress)
      if (isDryUp(currency))
        (Some(validAddress), true)
      else
        (Some(validAddress), false)
    }
  }

  def faucetAddress(currency: Currency, addresses: Set[Address]) {
    unusedAddresses.getOrElseUpdate(currency, Set.empty[Address]) ++= addresses
  }

  def getSupportedCurrency = supportedCurrency
}
