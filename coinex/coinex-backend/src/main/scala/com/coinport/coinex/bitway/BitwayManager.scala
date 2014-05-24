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
  val addressStatus = Map.empty[String, AddressStatus]
  val addressUidMap = Map.empty[String, Long]
  // TODO(c): remove confirmed tx
  val sigIdsSinceLastBlock = Set.empty[String]
  var lastAlive: Long = -1

  final val SPECIAL_ACCOUNT_ID: Map[CryptoCurrencyAddressType, Long] = Map(
    CryptoCurrencyAddressType.Hot -> -1,
    CryptoCurrencyAddressType.Cold -> -2
  )

  val FAUCET_THRESHOLD: Double = 0.5
  val INIT_ADDRESS_NUM: Int = 100

  def getSnapshot = TBitwayState(
    supportedCurrency,
    getFiltersSnapshot,
    blockIndexes.toList,
    addresses.map(kv => (kv._1 -> kv._2.clone)),
    addressStatus.map(kv => (kv._1 -> kv._2.toThrift)),
    lastAlive,
    addressUidMap.clone,
    sigIdsSinceLastBlock.clone
  )

  def loadSnapshot(s: TBitwayState) {
    blockIndexes.clear
    blockIndexes ++= s.blockIndexes.to[ArrayBuffer]
    addresses.clear
    addresses ++= s.addresses.map(kv => (kv._1 -> (Set.empty[String] ++ kv._2)))
    addressStatus.clear
    addressStatus ++= s.addressStatus.map(kv => (kv._1 -> AddressStatus(kv._2)))
    lastAlive = s.lastAlive
    loadFiltersSnapshot(s.filters)
    addressUidMap.clear
    addressUidMap ++= s.addressUidMap
    sigIdsSinceLastBlock.clear
    sigIdsSinceLastBlock ++= s.sigIdsSinceLastBlock
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
    addressStatus ++= addrs.map(i => (i -> AddressStatus()))
    if (SPECIAL_ACCOUNT_ID.contains(cryptoCurrencyAddressType))
      addressUidMap ++= addrs.map(_ -> SPECIAL_ACCOUNT_ID(cryptoCurrencyAddressType))
  }

  def updateLastAlive(ts: Long) {
    lastAlive = ts
  }

  def getSupportedCurrency = supportedCurrency

  def getBlockIndexes: Option[ArrayBuffer[BlockIndex]] = Option(blockIndexes)

  def getCurrentBlockIndex: Option[BlockIndex] = {
    if (blockIndexes.size > 0)
      Some(blockIndexes.last)
    else None
  }

  def getTransferType(inputs: Set[String], outputs: Set[String]): Option[TransferType] = {
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
    val CryptoCurrencyTransaction(_, _, _, inputs, outputs, _, _, _, status, _) = tx
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

  def updateBlocks(startIndex: Option[BlockIndex], blocks: Seq[CryptoCurrencyBlock]) {
    appendBlockChain(blocks.map(_.index).toList, startIndex)
    if (startIndex.isDefined && startIndex.get.height.isDefined) clearAmountAfterHeight(startIndex.get.height.get)
    blocks foreach { block =>
      updateAddressStatus(block.txs, block.index.height)
    }
  }

  def getNetworkStatus: CryptoCurrencyNetworkStatus = {
    getCurrentBlockIndex match {
      case None => CryptoCurrencyNetworkStatus(heartbeatTime = if (lastAlive != -1) Some(lastAlive) else None)
      case Some(index) => CryptoCurrencyNetworkStatus(index.id, index.height,
        if (lastAlive != -1) Some(lastAlive) else None)
    }
  }

  def getAddressStatus(t: CryptoCurrencyAddressType): Map[String, AddressStatusResult] = {
    Map(addresses(t).filter(d =>
      addressStatus.contains(d) && addressStatus(d) != AddressStatus()).toSeq.map(address =>
      (address -> addressStatus(address).getAddressStatusResult(getCurrentHeight))
    ): _*)
  }

  def notProcessed(tx: CryptoCurrencyTransaction): Boolean = {
    tx.sigId.isDefined && !sigIdsSinceLastBlock.contains(tx.sigId.get)
  }

  def rememberTx(tx: CryptoCurrencyTransaction) {
    sigIdsSinceLastBlock += tx.sigId.get
  }

  def canAdjustAddressAmount(address: String, adjustAmount: Long): Boolean = {
    (getAddressAmount(address) + adjustAmount) >= 0
  }

  def adjustAddressAmount(address: String, adjustAmount: Long) {
    val status = addressStatus.getOrElse(address, AddressStatus())
    status.updateBook(Some(-1), Some(adjustAmount))
    addressStatus += (address -> status)
  }

  def getAddressAmount(address: String): Long = {
    val status = addressStatus.getOrElse(address, AddressStatus())
    status.getAmount(getCurrentHeight, 1)
  }

  private def getCurrentHeight: Option[Long] = {
    blockIndexes.lastOption match {
      case None => None
      case Some(index) => index.height
    }
  }

  private[bitway] def updateAddressStatus(txs: Seq[CryptoCurrencyTransaction], h: Option[Long]) {
    txs.foreach {
      case CryptoCurrencyTransaction(_, Some(txid), _, Some(inputs), Some(outputs), _, _, _, _, _) =>
        def updateAddressStatus_(ports: Seq[CryptoCurrencyTransactionPort], isDeposit: Boolean) {
          ports.filter(port => addressStatus.contains(port.address)).foreach { port =>
            val addrStatus = addressStatus.getOrElse(port.address, AddressStatus())
            val newAddrStatus = addrStatus.updateTxid(Some(txid)).updateHeight(h).updateBook(h,
              port.amount.map(new CurrencyWrapper(_).internalValue(supportedCurrency) * (if (isDeposit) 1 else -1)))
            addressStatus += (port.address -> newAddrStatus)
          }
        }

        updateAddressStatus_(inputs, false)
        updateAddressStatus_(outputs, true)
      case _ => None
    }
  }

  private def clearAmountAfterHeight(h: Long) {
    addressStatus.keys.foreach { addr =>
      addressStatus.update(addr, addressStatus(addr).clearBookAfterHeight(h))
    }
  }

  private[bitway] def appendBlockChain(chain: List[BlockIndex], startIndex: Option[BlockIndex] = None) {
    val reorgPos = blockIndexes.indexWhere(Option(_) == startIndex) + 1
    if (reorgPos > 0) {
      blockIndexes.remove(reorgPos, blockIndexes.length - reorgPos)
    }
    blockIndexes ++= chain
    if (blockIndexes.length > maintainedChainLength)
      blockIndexes.remove(0, blockIndexes.length - maintainedChainLength)
  }
}
