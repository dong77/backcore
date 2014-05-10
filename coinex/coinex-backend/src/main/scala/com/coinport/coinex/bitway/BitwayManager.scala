/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map
import scala.collection.mutable.Set

import com.coinport.coinex.api.model._
import com.coinport.coinex.common.Manager
import com.coinport.coinex.data._
import Currency._

object BlockContinuityEnum extends Enumeration {
  type BlockContinuity = Value
  val SUCCESSOR, GAP, REORG, OTHER_BRANCH, DUP = Value
}

object BitwayManager {
  final val INDEX_LIST_LIMIT: Map[Currency, Int] = Map(
    Btc -> 10,
    Ltc -> 20,
    Pts -> 30
  )
}

class BitwayManager extends Manager[TBitwayState] {

  val unusedAddresses = Map.empty[Currency, Set[String]]
  val usedAddresses = Map.empty[Currency, Set[String]]
  val hotAddresses = Map.empty[Currency, Set[String]]
  val coldAddresses = Map.empty[Currency, Set[String]]
  val blockIndexes = Map.empty[Currency, ArrayBuffer[BlockIndex]]
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
    blockIndexes ++= s.stats.map(kv => (kv._1 -> (kv._2.blockIndexes.to[ArrayBuffer])))
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

  def getBlockIndexes(currency: Currency): Option[ArrayBuffer[BlockIndex]] = blockIndexes.get(currency)

  def getCurrentBlockIndex(currency: Currency): Option[BlockIndex] = {
    blockIndexes.get(currency) match {
      case None => None
      case Some(indexes) if indexes.size > 0 => Some(indexes(0))
      case _ => None
    }
  }

  def getCryptoCurrencyTransactionType(currency: Currency, inputs: Set[String],
    outputs: Set[String]): Option[CryptoCurrencyTransactionType] = {
    object AddressSetEnum extends Enumeration {
      type AddressSet = Value
      val UNUSED, USED, HOT, COLD = Value
    }

    import AddressSetEnum._

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

    // Transfer will disable someone withdrawal to his deposit address.
    // Which means one CryptoCurrencyTransaction can't has two types: Deposit as well as Withdrawal
    val inputsMatched = getIntersectSet(currency, inputs)
    val outputsMatched = getIntersectSet(currency, outputs)
    if (inputsMatched.contains(USED) && outputsMatched.contains(HOT)) {
      Some(CryptoCurrencyTransactionType.UserToHot)
    } else if (inputsMatched.contains(HOT)) {
      assert(outputsMatched.contains(USED))
      if (outputsMatched.contains(COLD)) {
        Some(CryptoCurrencyTransactionType.HotToCold)
      } else {
        Some(CryptoCurrencyTransactionType.Withdrawal)
      }
    } else if (inputsMatched.contains(COLD) && outputsMatched.contains(HOT)) {
      Some(CryptoCurrencyTransactionType.ColdToHot)
    } else if (outputsMatched.contains(USED)) {
      Some(CryptoCurrencyTransactionType.Deposit)
    } else if (inputsMatched.nonEmpty || outputsMatched.nonEmpty) {
      Some(CryptoCurrencyTransactionType.Unknown)
    } else {
      None
    }
  }

  import BlockContinuityEnum._

  def getBlockContinuity(currency: Currency,
    blocksMsg: CryptoCurrencyBlocksMessage): BlockContinuity = {
    getBlockIndexes(currency) match {
      case None => SUCCESSOR
      case Some(indexList) if indexList.size > 0 =>
        assert(blocksMsg.blocks.size > 0)
        blocksMsg.startIndex match {
          case None =>
            if (blocksMsg.blocks.head.prevIndex.id == indexList.last.id)
              SUCCESSOR
            else if (indexList.exists(i => i.id == blocksMsg.blocks.head.index.id))
              DUP
            else
              GAP
          case Some(BlockIndex(Some(id), _)) =>
            if (id == indexList.last.id) SUCCESSOR else REORG
          case Some(BlockIndex(None, _)) => OTHER_BRANCH
        }
      case _ => SUCCESSOR
    }
  }

  def completeCryptoCurrencyTransaction(
    currency: Currency,
    tx: CryptoCurrencyTransaction,
    prevBlock: Option[BlockIndex] = None,
    includedBlock: Option[BlockIndex] = None): Option[CryptoCurrencyTransaction] = {
    val CryptoCurrencyTransaction(_, _, _, inputs, outputs, _, _, _, status) = tx
    val txType = getCryptoCurrencyTransactionType(currency, Set.empty[String] ++ inputs.get.map(_.address),
      Set.empty[String] ++ outputs.get.map(_.address))
    if (txType.isDefined) {
      val regularizeInputs = inputs.map(_.map(i => i.copy(innerAmount = i.amount.map(_.internalValue(currency)))))
      val regularizeOutputs = outputs.map(_.map(i => i.copy(innerAmount = i.amount.map(_.internalValue(currency)))))
      Some(tx.copy(inputs = regularizeInputs, outputs = regularizeOutputs,
        prevBlock = if (prevBlock.isDefined) prevBlock else getCurrentBlockIndex(currency),
        includedBlock = includedBlock, txType = txType))
    } else {
      None
    }
  }

  def extractTxsFromBlocks(currency: Currency, blocks: List[CryptoCurrencyBlock]): List[CryptoCurrencyTransaction] = {
    blocks.flatMap { block =>
      val CryptoCurrencyBlock(index, prevIndex, txsInBlock) = block
      txsInBlock.map(completeCryptoCurrencyTransaction(
        currency, _, Some(prevIndex), Some(index))).filter(_.isDefined).map(_.get)
    }
  }

  import BitwayManager._

  def appendBlockChain(currency: Currency, chain: List[BlockIndex], startIndex: Option[BlockIndex] = None) {
    val indexList = blockIndexes.getOrElseUpdate(currency, ArrayBuffer.empty[BlockIndex])
    val reorgPos = indexList.indexWhere(Option(_) == startIndex) + 1
    if (reorgPos > 0) {
      indexList.remove(reorgPos, indexList.length - reorgPos)
    }
    indexList ++= chain
    if (indexList.length > INDEX_LIST_LIMIT.getOrElseUpdate(currency, 10))
      indexList.remove(0, indexList.length - INDEX_LIST_LIMIT(currency))
  }
}
