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

class BitwayManager(supportedCurrency: Currency, maintainedChainLength: Int) extends Manager[TBitwayState] {

  import CryptoCurrencyAddressType._

  val blockIndexes = ArrayBuffer.empty[BlockIndex]
  val addresses: Map[CryptoCurrencyAddressType, Set[String]] = Map(
    CryptoCurrencyAddressType.list.map(_ -> Set.empty[String]): _*)
  val addressLastTx = Map.empty[String, BlockIndex]
  val addressUidMap = Map.empty[String, Long]
  var lastAlive: Long = -1

  val FAUCET_THRESHOLD: Double = 0.5
  val INIT_ADDRESS_NUM: Int = 100

  def getSnapshot = TBitwayState(
    supportedCurrency,
    getFiltersSnapshot,
    blockIndexes,
    addresses.map(kv => (kv._1 -> kv._2.clone)),
    addressLastTx.clone,
    lastAlive,
    addressUidMap.clone
  )

  def loadSnapshot(s: TBitwayState) {
    blockIndexes.clear
    blockIndexes ++= s.blockIndexes.to[ArrayBuffer]
    addresses.clear
    addresses ++= s.addresses.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2)))
    addressLastTx.clear
    addressLastTx ++= s.addressLastTx
    lastAlive = s.lastAlive
    loadFiltersSnapshot(s.filters)
    addressUidMap.clear
    addressUidMap ++= s.addressUidMap
  }

  def isDryUp = addresses(Unused).size == 0 || addresses(UserUsed).size > addresses(Unused).size * FAUCET_THRESHOLD

  def allocateAddress: (Option[String], Boolean /* need fetch from bitway */ ) = {
    if (addresses(Unused).isEmpty) {
      (None, true)
    } else {
      val validAddress = addresses(Unused).headOption
      if (isDryUp)
        (validAddress, true)
      else
        (validAddress, false)
    }
  }

  def addressAllocated(uid: Long, address: String) {
    assert(addresses(Unused).contains(address))
    addresses(Unused).remove(address)
    addresses(UserUsed).add(address)
    addressUidMap += (address -> uid)
  }

  def faucetAddress(cryptoCurrencyAddressType: CryptoCurrencyAddressType, addrs: Set[String]) {
    addresses(cryptoCurrencyAddressType) ++= addrs
    addressLastTx ++= addrs.map(i => (i -> BlockIndex()))
  }

  def updateLastTx(txs: Seq[CryptoCurrencyTransaction]) {
    txs.foreach {
      case CryptoCurrencyTransaction(_, Some(txid), _, Some(inputs), Some(outputs), _, Some(includedBlock), _, _) =>
        (inputs ++ outputs).filter(port => addressLastTx.contains(port.address)).foreach { port =>
          if (includedBlock.height.isDefined)
            addressLastTx += (port.address -> BlockIndex(Some(txid), includedBlock.height))
        }
      case _ => None
    }
  }

  def updateLastAlive(ts: Long) {
    lastAlive = ts
  }

  def getSupportedCurrency = supportedCurrency

  def getBlockIndexes: Option[ArrayBuffer[BlockIndex]] = Option(blockIndexes)

  def getCurrentBlockIndex: Option[BlockIndex] = {
    if (blockIndexes.size > 0)
      Some(blockIndexes(0))
    else None
  }

  def getTransferType(inputs: Set[String],
    outputs: Set[String]): Option[TransferType] = {
    object AddressSetEnum extends Enumeration {
      type AddressSet = Value
      val UNUSED, USED, HOT, COLD = Value
    }

    import AddressSetEnum._

    def getIntersectSet(set: Set[String]): ValueSet = {
      var enumSet = ValueSet.empty
      if ((set & addresses(Unused)).nonEmpty)
        enumSet += UNUSED
      if ((set & addresses(UserUsed)).nonEmpty)
        enumSet += USED
      if ((set & addresses(Hot)).nonEmpty)
        enumSet += HOT
      if ((set & addresses(Cold)).nonEmpty)
        enumSet += COLD
      return enumSet
    }

    // Transfer will disable someone withdrawal to his deposit address.
    // Which means one CryptoCurrencyTransaction can't has two types: Deposit as well as Withdrawal
    val inputsMatched = getIntersectSet(inputs)
    val outputsMatched = getIntersectSet(outputs)
    if (inputsMatched.contains(USED) && outputsMatched.contains(HOT)) {
      Some(TransferType.UserToHot)
    } else if (inputsMatched.contains(HOT)) {
      assert(!outputsMatched.contains(USED))
      if (outputsMatched.contains(COLD)) {
        Some(TransferType.HotToCold)
      } else {
        Some(TransferType.Withdrawal)
      }
    } else if (inputsMatched.contains(COLD) && outputsMatched.contains(HOT)) {
      Some(TransferType.ColdToHot)
    } else if (outputsMatched.contains(USED)) {
      Some(TransferType.Deposit)
    } else if (inputsMatched.nonEmpty || outputsMatched.nonEmpty) {
      Some(TransferType.Unknown)
    } else {
      None
    }
  }

  import BlockContinuityEnum._

  def getBlockContinuity(blocksMsg: CryptoCurrencyBlocksMessage): BlockContinuity = {
    getBlockIndexes match {
      case None => SUCCESSOR
      case Some(indexList) if indexList.size > 0 =>
        assert(blocksMsg.blocks.size > 0)
        blocksMsg.reorgIndex match {
          case None =>
            if (blocksMsg.blocks.head.prevIndex.id == indexList.last.id)
              SUCCESSOR
            else if (indexList.exists(i => i.id == blocksMsg.blocks.head.index.id))
              DUP
            else
              GAP
          case Some(BlockIndex(Some(id), _)) =>
            if (Some(id) == indexList.last.id) SUCCESSOR else REORG
          case Some(BlockIndex(None, _)) => OTHER_BRANCH
        }
      case _ => SUCCESSOR
    }
  }

  def completeTransferInfos(infos: Seq[CryptoCurrencyTransferInfo],
    isHotToCold: Boolean = false): (Seq[CryptoCurrencyTransferInfo], Boolean /* isFail */ ) = {
    if (isHotToCold) {
      if (addresses(Cold).isEmpty) {
        (Nil, true)
      } else {
        (infos.map(info => info.copy(amount = info.internalAmount.map((new CurrencyWrapper(_).externalValue(
          supportedCurrency))), to = Some(addresses(Cold).head))), false)
      }
    } else {
      (infos.map(info => info.copy(amount = info.internalAmount.map((new CurrencyWrapper(_).externalValue(
        supportedCurrency))))), false)
    }
  }

  def completeCryptoCurrencyTransaction(
    tx: CryptoCurrencyTransaction,
    prevBlock: Option[BlockIndex] = None,
    includedBlock: Option[BlockIndex] = None): Option[CryptoCurrencyTransaction] = {
    val CryptoCurrencyTransaction(_, _, _, inputs, outputs, _, _, _, status) = tx
    if (!inputs.isDefined || !outputs.isDefined) {
      None
    } else {
      val txType = getTransferType(Set.empty[String] ++ inputs.get.map(_.address),
        Set.empty[String] ++ outputs.get.map(_.address))
      if (txType.isDefined) {
        val regularizeInputs = inputs.map(_.map(i => i.copy(
          internalAmount = i.amount.map(new CurrencyWrapper(_).internalValue(supportedCurrency)),
          userId = addressUidMap.get(i.address))))
        val regularizeOutputs = outputs.map(_.map(i => i.copy(
          internalAmount = i.amount.map(new CurrencyWrapper(_).internalValue(supportedCurrency)),
          userId = addressUidMap.get(i.address))))
        Some(tx.copy(inputs = regularizeInputs, outputs = regularizeOutputs,
          prevBlock = if (prevBlock.isDefined) prevBlock else getCurrentBlockIndex,
          includedBlock = includedBlock, txType = txType))
      } else {
        None
      }
    }
  }

  def extractTxsFromBlocks(blocks: List[CryptoCurrencyBlock]): List[CryptoCurrencyTransaction] = {
    blocks.flatMap { block =>
      val CryptoCurrencyBlock(index, prevIndex, txsInBlock) = block
      val filteredTxs = txsInBlock.map(completeCryptoCurrencyTransaction(_, Some(prevIndex), Some(index))).filter(
        _.isDefined).map(_.get)
      if (filteredTxs.isEmpty) {
        List(CryptoCurrencyTransaction(prevBlock = Some(prevIndex), includedBlock = Some(index),
          status = TransferStatus.Confirming))
      } else {
        filteredTxs
      }
    }
  }

  def appendBlockChain(chain: List[BlockIndex], startIndex: Option[BlockIndex] = None) {
    val reorgPos = blockIndexes.indexWhere(Option(_) == startIndex) + 1
    if (reorgPos > 0) {
      blockIndexes.remove(reorgPos, blockIndexes.length - reorgPos)
    }
    blockIndexes ++= chain
    if (blockIndexes.length > maintainedChainLength)
      blockIndexes.remove(0, blockIndexes.length - maintainedChainLength)
  }

  def getLastAlive = lastAlive

  def getLastTxs(t: CryptoCurrencyAddressType): Map[String, BlockIndex] = {
    Map(addresses(t).filter(d => addressLastTx.contains(d) && addressLastTx(d) != BlockIndex(None, None)).toSeq.map(address =>
      (address -> addressLastTx(address))
    ): _*)
  }
}
