/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import scala.collection.mutable.Map
import scala.collection.mutable.Set

import com.coinport.coinex.common.Manager
import com.coinport.coinex.data._
import Currency._

class BitwayManager extends Manager[TBitwayState] {

  val unusedAddresses = Map.empty[Currency, Set[String]]
  val usedAddresses = Map.empty[Currency, Set[String]]
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
    unusedAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2.unusedAddresses)))
    usedAddresses.clear
    usedAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2.usedAddresses)))
    cursors.clear
    cursors ++= s.stats.map(kv => (kv._1 -> kv._2.cursor))
  }

  def isDryUp(currency: Currency) = (unusedAddresses.getOrElseUpdate(currency, Set.empty[String]).size == 0 ||
    usedAddresses.getOrElseUpdate(currency, Set.empty[String]).size > unusedAddresses.getOrElseUpdate(
      currency, Set.empty[String]).size * FAUCET_THRESHOLD)

  def allocateAddress(currency: Currency): (Option[String], Boolean /* need fetch from bitway */ ) = {
    if (!unusedAddresses.contains(currency)) {
      (None, true)
    } else {
      val addresses = unusedAddresses(currency)
      if (addresses.isEmpty) {
        (None, true)
      } else {
        val validAddress = addresses.headOption
        if (isDryUp(currency))
          (validAddress, true)
        else
          (validAddress, false)
      }
    }
  }

  def addressAllocated(currency: Currency, address: String) {
    assert(unusedAddresses.contains(currency))
    val addresses = unusedAddresses(currency)
    assert(addresses.contains(address))
    addresses.remove(address)
    usedAddresses.getOrElseUpdate(currency, Set.empty[String]).add(address)
  }

  def faucetAddress(currency: Currency, addresses: Set[String]) {
    unusedAddresses.getOrElseUpdate(currency, Set.empty[String]) ++= addresses
  }

  def getSupportedCurrency = supportedCurrency
}
