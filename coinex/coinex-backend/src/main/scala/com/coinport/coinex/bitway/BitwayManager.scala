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

object AddressSetEnum extends Enumeration {
  type AddressSet = Value
  val UNUSED, USED, HOT, COLD = Value
}

class BitwayManager extends Manager[TBitwayState] {

  import AddressSetEnum._

  val unusedAddresses = Map.empty[Currency, Set[String]]
  val usedAddresses = Map.empty[Currency, Set[String]]
  val hotAddresses = Map.empty[Currency, Set[String]]
  val coldAddresses = Map.empty[Currency, Set[String]]
  val blockIndexes = Map.empty[Currency, List[BlockIndex]]
  val supportedCurrency = Set[Currency](Btc) // TODO(c): put this to config file

  val FAUCET_THRESHOLD: Double = 0.5
  val INIT_ADDRESS_NUM: Int = 100

  def getSnapshot = TBitwayState(blockIndexes.map(kv =>
    (kv._1 -> CurrencyNetwork(
      kv._1, kv._2,
      unusedAddresses(kv._1).clone,
      usedAddresses(kv._1).clone,
      hotAddresses(kv._1).clone,
      coldAddresses(kv._1).clone)
    )));

  def loadSnapshot(s: TBitwayState) {
    unusedAddresses.clear
    unusedAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2.unusedAddresses)))
    usedAddresses.clear
    usedAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2.usedAddresses)))
    hotAddresses.clear
    hotAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2.hotAddresses)))
    coldAddresses.clear
    coldAddresses ++= s.stats.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2.coldAddresses)))
    blockIndexes.clear
    blockIndexes ++= s.stats.map(kv => (kv._1 -> (List.empty[BlockIndex] ++ kv._2.blockIndexes)))
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

  def getIntersectSet(currency: Currency, set: Set[String]): ValueSet = {
    var enumSet = ValueSet.empty
    if ((set & unusedAddresses.getOrElse(currency, Set.empty[String])).nonEmpty)
      enumSet += UNUSED
    if ((set & usedAddresses.getOrElse(currency, Set.empty[String])).nonEmpty)
      enumSet += USED
    if ((set & hotAddresses.getOrElse(currency, Set.empty[String])).nonEmpty)
      enumSet += HOT
    if ((set & coldAddresses.getOrElse(currency, Set.empty[String])).nonEmpty)
      enumSet += COLD
    return enumSet
  }

  def getBlockIndexes(currency: Currency): Option[List[BlockIndex]] = blockIndexes.get(currency)

  def getCurrentBlockIndex(currency: Currency): Option[BlockIndex] = {
    blockIndexes.get(currency) match {
      case None => None
      case Some(indexes) if indexes.size > 0 => Some(indexes(0))
      case _ => None
    }
  }

  def getCCTxType(currency: Currency, inputs: Set[String], outputs: Set[String]): Option[CCTxType] = {
    // Transfer will disable someone withdrawal to his deposit address.
    // Which means one CCTx has two types: Deposit as well as Withdrawal
    val inputsMatched = getIntersectSet(currency, inputs)
    val outputsMatched = getIntersectSet(currency, outputs)
    if (inputsMatched.contains(USED) && outputsMatched.contains(HOT)) {
      Some(CCTxType.UserToHot)
    } else if (inputsMatched.contains(HOT)) {
      assert(outputsMatched.contains(USED))
      if (outputsMatched.contains(COLD)) {
        Some(CCTxType.HotToCold)
      } else {
        Some(CCTxType.Withdrawal)
      }
    } else if (inputsMatched.contains(COLD) && outputsMatched.contains(HOT)) {
      Some(CCTxType.ColdToHot)
    } else if (outputsMatched.contains(USED)) {
      Some(CCTxType.Deposit)
    } else if (inputsMatched.nonEmpty || outputsMatched.nonEmpty) {
      Some(CCTxType.Unknown)
    } else {
      None
    }
  }
}
