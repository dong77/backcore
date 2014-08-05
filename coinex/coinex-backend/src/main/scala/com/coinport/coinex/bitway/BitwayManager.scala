/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import org.slf4s.Logging
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map
import scala.collection.mutable.Set

import com.coinport.coinex.api.model._
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.common.Manager
import com.coinport.coinex.data._
import com.coinport.coinex.data.TransferType.DepositHot
import Currency._

object BlockContinuityEnum extends Enumeration {
  type BlockContinuity = Value
  val SUCCESSOR, GAP, REORG, OTHER_BRANCH, DUP, BAD = Value
}

class BitwayManager(supportedCurrency: Currency, config: BitwayConfig)
    extends Manager[TBitwayState] with Logging {

  import CryptoCurrencyAddressType._

  val blockIndexes = ArrayBuffer.empty[BlockIndex]
  val addresses: Map[CryptoCurrencyAddressType, Set[String]] = Map(
    CryptoCurrencyAddressType.list.map(_ -> Set.empty[String]): _*)
  val addressStatus = Map.empty[String, AddressStatus]
  val addressUidMap = Map.empty[String, Long]
  // TODO(c): remove confirmed tx
  val sigIdsSinceLastBlock = Set.empty[String]
  var lastAlive: Long = -1
  private[bitway] val privateKeysBackup = Map.empty[String, String]
  private[bitway] val address2AccountNameMap = Map.empty[String, String]
  private[bitway] val address2NxtRsAddressMap = Map.empty[String, String]

  final val SPECIAL_ACCOUNT_ID: Map[CryptoCurrencyAddressType, Long] = Map(
    CryptoCurrencyAddressType.Hot -> HOT_UID,
    CryptoCurrencyAddressType.Cold -> COLD_UID
  )

  val FAUCET_THRESHOLD: Double = 0.5
  val INIT_ADDRESS_NUM: Int = 100

  if (config.coldAddresses.nonEmpty)
    syncColdAddresses(config.coldAddresses)

  def getSnapshot = TBitwayState(
    supportedCurrency,
    getFiltersSnapshot,
    blockIndexes.toList,
    addresses.map(kv => (kv._1 -> kv._2.clone)),
    addressStatus.map(kv => (kv._1 -> kv._2.toThrift)),
    lastAlive,
    addressUidMap.clone,
    sigIdsSinceLastBlock.clone,
    privateKeysBackup = if (privateKeysBackup.size > 0) Some(privateKeysBackup.clone) else None,
    address2AccountNameMap = if (address2AccountNameMap.nonEmpty) Some(address2AccountNameMap.clone) else None,
    address2NxtRsAddressMap = if (address2NxtRsAddressMap.nonEmpty) Some(address2NxtRsAddressMap.clone) else None
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
    if (s.privateKeysBackup.isDefined) {
      privateKeysBackup.clear
      privateKeysBackup ++= s.privateKeysBackup.get
    }
    if (s.address2AccountNameMap.isDefined) {
      address2AccountNameMap.clear
      address2AccountNameMap ++= s.address2AccountNameMap.get
    }
    if (s.address2NxtRsAddressMap.isDefined) {
      address2NxtRsAddressMap.clear
      address2NxtRsAddressMap ++= s.address2NxtRsAddressMap.get
    }

    if (config.coldAddresses.nonEmpty)
      syncColdAddresses(config.coldAddresses)
  }

  def isDryUp = addresses(Unused).size == 0 || addresses(User).size > addresses(Unused).size * FAUCET_THRESHOLD

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
    addresses(User).add(address)
    addressUidMap += (address -> uid)
  }

  def faucetAddress(cryptoCurrencyAddressType: CryptoCurrencyAddressType, addrs: Set[CryptoAddress]) {
    addresses(cryptoCurrencyAddressType) ++= addrs.map(_.address)
    addressStatus ++= addrs.map(i => (i.address -> AddressStatus()))
    if (SPECIAL_ACCOUNT_ID.contains(cryptoCurrencyAddressType))
      addressUidMap ++= addrs.map(_.address -> SPECIAL_ACCOUNT_ID(cryptoCurrencyAddressType))
    val privateKeys = addrs.filter(_.privateKey.isDefined)
    if (privateKeys.size > 0)
      privateKeysBackup ++= Map(privateKeys.map(i => (i.address -> i.privateKey.get)).toSeq: _*)
    val accountNames = addrs.filter(_.accountName.isDefined)
    if (accountNames.nonEmpty)
      address2AccountNameMap ++= Map(accountNames.map(i => (i.address -> i.accountName.get)).toSeq: _*)
    val nxtRsNames = addrs.filter(_.nxtRsAddress.isDefined)
    if (nxtRsNames.nonEmpty)
      address2NxtRsAddressMap ++= Map(nxtRsNames.map(i => (i.address -> i.nxtRsAddress.get)).toSeq: _*)
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
      val UNUSED, USER, HOT, COLD = Value
    }

    import AddressSetEnum._

    def getIntersectSet(set: Set[String]): ValueSet = {
      var enumSet = ValueSet.empty
      if ((set & addresses(Unused)).nonEmpty)
        enumSet += UNUSED
      if ((set & addresses(User)).nonEmpty)
        enumSet += USER
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
    if (inputsMatched.contains(USER) && outputsMatched.contains(HOT)) {
      Some(TransferType.UserToHot)
    } else if (inputsMatched.contains(HOT)) {
      if (outputsMatched.contains(COLD))
        Some(TransferType.HotToCold)
      else
        Some(TransferType.Withdrawal)
    } else if (inputsMatched.contains(COLD)) {
      if (outputsMatched.contains(HOT))
        Some(TransferType.ColdToHot)
      else
        Some(TransferType.Withdrawal)
    } else if (outputsMatched.contains(USER)) {
      if (!inputsMatched.contains(USER))
        Some(TransferType.Deposit)
      else
        Some(TransferType.Unknown)
    } else if (config.isDepositHot && outputsMatched.contains(HOT)) {
      Some(TransferType.DepositHot)
    } else if (inputsMatched.nonEmpty || outputsMatched.nonEmpty) {
      Some(TransferType.Unknown)
    } else {
      None
    }
  }

  import BlockContinuityEnum._

  def getBlockContinuity(blockMsg: CryptoCurrencyBlockMessage): BlockContinuity = {
    getBlockIndexes match {
      case None => SUCCESSOR
      case Some(indexList) if indexList.size > 0 =>
        blockMsg.reorgIndex match {
          case None =>
            if (blockMsg.block.prevIndex == indexList.last)
              if (blockMsg.block.index.height.get - 1 == indexList.last.height.get)
                SUCCESSOR
              else
                BAD
            else if (indexList.exists(i => i.id == blockMsg.block.index.id) ||
              blockMsg.block.index.height.get < indexList.head.height.get)
              DUP
            else
              GAP
          case Some(ri @ BlockIndex(Some(id), Some(h))) =>
            if (ri == indexList.last)
              if (blockMsg.block.index.height.get - 1 == indexList.last.height.get)
                SUCCESSOR
              else
                BAD
            else if (indexList.exists(i => i.id == blockMsg.block.index.id) ||
              blockMsg.block.index.height.get < indexList.head.height.get)
              DUP
            else if (indexList.exists(i => i.id == ri.id))
              REORG
            else
              BAD
          case Some(BlockIndex(None, _)) => OTHER_BRANCH
        }
      case _ if blockMsg.block == null =>
        BAD
      case _ => SUCCESSOR
    }
  }

  def completeTransferInfos(infos: Seq[CryptoCurrencyTransferInfo],
    isHotToCold: Boolean = false): (List[CryptoCurrencyTransferInfo], Boolean /* isFail */ ) = {
    if (isHotToCold) {
      if (addresses(Cold).isEmpty) {
        (infos.map(i => i.copy(error = Some(ErrorCode.NoAddressFound))).toList, true)
      } else {
        (infos.map(info => info.copy(amount = info.internalAmount.map((new CurrencyWrapper(_).externalValue(
          supportedCurrency))), to = Some(addresses(Cold).head))).toList, false)
      }
    } else {
      (infos.map(info => info.copy(amount = info.internalAmount.map((new CurrencyWrapper(_).externalValue(
        supportedCurrency))))).toList, false)
    }
  }

  def completeCryptoCurrencyTransaction(
    tx: CryptoCurrencyTransaction,
    prevBlock: Option[BlockIndex] = None,
    includedBlock: Option[BlockIndex] = None): Option[CryptoCurrencyTransaction] = {
    val CryptoCurrencyTransaction(_, _, _, inputs, outputs, _, _, _, status, _, _) = tx
    if (!inputs.isDefined || !outputs.isDefined) {
      None
    } else {
      val txType =
        if (tx.txType.isDefined)
          tx.txType
        else
          getTransferType(Set.empty[String] ++ inputs.get.map(_.address),
            Set.empty[String] ++ outputs.get.map(_.address))
      if (txType.isDefined) {
        val regularizeInputs = inputs.map(_.map(i => i.copy(
          internalAmount = i.amount.map(new CurrencyWrapper(_).internalValue(supportedCurrency)),
          userId = addressUidMap.get(i.address), accountName = address2AccountNameMap.get(i.address), nxtRsAddress = getNxtRsAddress(i))))
        val regularizeOutputs = outputs.map(_.map(i => i.copy(
          internalAmount = i.amount.map(new CurrencyWrapper(_).internalValue(supportedCurrency)),
          userId = getUserId(i, tx, txType.get), accountName = address2AccountNameMap.get(i.address), nxtRsAddress = getNxtRsAddress(i))))
        val sumInput = regularizeInputs.get.map(i => i.internalAmount.getOrElse(0L)).sum
        val sumOutput = regularizeOutputs.get.map(i => i.internalAmount.getOrElse(0L)).sum
        val minerFee = if (sumInput > sumOutput) {
          Some(sumInput - sumOutput)
        } else {
          None
        }

        Some(tx.copy(inputs = regularizeInputs, outputs = regularizeOutputs,
          prevBlock = if (prevBlock.isDefined) prevBlock else getCurrentBlockIndex,
          includedBlock = includedBlock, txType = txType, minerFee = minerFee))
      } else {
        None
      }
    }
  }

  def extractTxsFromBlock(block: CryptoCurrencyBlock): List[CryptoCurrencyTransaction] = {
    val CryptoCurrencyBlock(index, prevIndex, txsInBlock) = block
    val filteredTxs = txsInBlock.map(completeCryptoCurrencyTransaction(_, Some(prevIndex), Some(index))).filter(
      _.isDefined).map(_.get)
    if (filteredTxs.isEmpty) {
      List(CryptoCurrencyTransaction(prevBlock = Some(prevIndex), includedBlock = Some(index),
        status = TransferStatus.Confirming))
    } else {
      filteredTxs.toList
    }
  }

  def updateBlock(startIndex: Option[BlockIndex], block: CryptoCurrencyBlock) {
    appendBlockChain(List(block.index), startIndex)
    if (startIndex.isDefined && startIndex.get.height.isDefined) clearAmountAfterHeight(startIndex.get.height.get)
    updateAddressStatus(block.txs, block.index.height)
  }

  def getNetworkStatus: CryptoCurrencyNetworkStatus = {
    getCurrentBlockIndex match {
      case None => CryptoCurrencyNetworkStatus(heartbeatTime = if (lastAlive != -1) Some(lastAlive) else None,
        queryTimestamp = Some(System.currentTimeMillis))
      case Some(index) => CryptoCurrencyNetworkStatus(index.id, index.height,
        if (lastAlive != -1) Some(lastAlive) else None, Some(System.currentTimeMillis))
    }
  }

  def getAddressStatus(t: CryptoCurrencyAddressType, confirmationNum: Option[Int] = None): Map[String, AddressStatusResult] = {
    Map(addresses(t).filter(d =>
      addressStatus.contains(d) && addressStatus(d) != AddressStatus()).toSeq.map(address =>
      (address -> addressStatus(address).getAddressStatusResult(getCurrentHeight, confirmationNum))
    ): _*)
  }

  def getReserveAmounts: Map[CryptoCurrencyAddressType, Long] = Map(
    User -> getReserveAmount(User),
    Hot -> getReserveAmount(Hot),
    Cold -> getReserveAmount(Cold)
  )

  def notProcessed(tx: CryptoCurrencyTransaction): Boolean = {
    tx.sigId.isDefined && !sigIdsSinceLastBlock.contains(tx.sigId.get)
  }

  def rememberTx(tx: CryptoCurrencyTransaction) {
    if (tx.sigId.isDefined)
      sigIdsSinceLastBlock += tx.sigId.get
  }

  def canAdjustAddressAmount(address: String, adjustAmount: Long): Boolean = {
    (getAddressAmount(address) + adjustAmount) >= 0
  }

  def adjustAddressAmount(address: String, adjustAmount: Long) {
    val status = addressStatus.getOrElse(address, AddressStatus())
    addressStatus += (address -> status.updateBook(Some(-1), Some(adjustAmount)))
  }

  def getAddressAmount(address: String): Long = {
    val status = addressStatus.getOrElse(address, AddressStatus())
    status.getAmount(getCurrentHeight, 1)
  }

  def includeWithdrawalToBadAddress(transferType: TransferType, infos: Seq[CryptoCurrencyTransferInfo]): Boolean = {
    if (transferType == TransferType.Withdrawal) {
      infos.exists(info => isWithdrawalToBadAddress(info))
    } else {
      false
    }
  }

  def isWithdrawalToBadAddress(info: CryptoCurrencyTransferInfo): Boolean = {
    info.to.isDefined && (addresses(User).contains(info.to.get) ||
      addresses(Hot).contains(info.to.get) || addresses(Cold).contains(info.to.get)
    )
  }

  def syncPrivateKeys(keys: List[CryptoAddress]) {
    privateKeysBackup.clear
    privateKeysBackup ++= Map(keys.map(i => (i.address -> i.privateKey.getOrElse("no-priv-key"))): _*)
  }

  def getPubKeys() = privateKeysBackup.keySet

  def syncHotAddresses(addrs: Set[CryptoAddress]) {
    val origAddresses = addresses.getOrElse(Hot, Set.empty[String])
    val unseenAddresses = addrs.filter(i => !origAddresses.contains(i.address))
    if (unseenAddresses.size > 0)
      faucetAddress(Hot, unseenAddresses)
  }

  def syncColdAddresses(addrs: List[String]) {
    val origAddresses = addresses.getOrElse(Cold, Set.empty[String])
    val unseenAddresses = addrs.filter(i => !origAddresses.contains(i))
    if (unseenAddresses.size > 0)
      faucetAddress(Cold, Set.empty[CryptoAddress] ++ unseenAddresses.map(CryptoAddress(_)))
  }

  def cleanBlockChain() {
    blockIndexes.clear
  }

  // for return value amount, +amount means transfer from hot to cold;
  //                          -amount means transfer from cold to hot.
  def needHotColdTransfer(): Option[Long] = {
    val hotAmount = getAvailableReserveAmount(CryptoCurrencyAddressType.Hot, Some(config.confirmNum))
    val coldAmount = getAvailableReserveAmount(CryptoCurrencyAddressType.Cold, Some(config.confirmNum))
    if (hotAmount <= config.hotColdTransferNumThreshold && coldAmount <= config.hotColdTransferNumThreshold) {
      return None
    }
    val HotColdTransferStrategy(highThreshold, lowThreshold) = config.hotColdTransfer.getOrElse(HotColdTransferStrategy(1, 0))
    val mid = (highThreshold + lowThreshold) / 2
    val allAmount = hotAmount + coldAmount
    if (allAmount == 0) {
      None
    } else {
      val hotPercent = hotAmount.toDouble / allAmount
      if (hotPercent <= highThreshold && hotPercent >= lowThreshold) {
        None
      } else {
        Some((hotAmount - allAmount * mid).toLong)
      }
    }
  }

  def getReserveAmount(t: CryptoCurrencyAddressType, confirmationNum: Option[Int] = None) = getAddressStatus(t, confirmationNum).values.map(_.confirmedAmount).sum

  def getAvailableReserveAmount(t: CryptoCurrencyAddressType, confirmationNum: Option[Int] = None) = {
    Math.min(getReserveAmount(t, confirmationNum), getReserveAmount(t))
  }

  private def getCurrentHeight: Option[Long] = {
    blockIndexes.lastOption match {
      case None => None
      case Some(index) => index.height
    }
  }

  private[bitway] def updateAddressStatus(txs: Seq[CryptoCurrencyTransaction], h: Option[Long]) {
    txs.foreach {
      case CryptoCurrencyTransaction(_, Some(txid), _, Some(inputs), Some(outputs), _, _, _, _, _, _) =>
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

  private[bitway] def appendBlockChain(chain: Seq[BlockIndex], startIndex: Option[BlockIndex] = None) = {
    val reorgPos = blockIndexes.indexWhere(Option(_) == startIndex) + 1
    if (reorgPos <= 0 && startIndex.isDefined) {
      log.warn("try to append non-successor block. startIndex: " + startIndex + ", chain: " + chain)
      log.warn("the maintained index list: " + blockIndexes)
    }
    if (reorgPos > 0)
      blockIndexes.remove(reorgPos, blockIndexes.length - reorgPos)
    blockIndexes ++= chain
    if (blockIndexes.length > config.maintainedChainLength)
      blockIndexes.remove(0, blockIndexes.length - config.maintainedChainLength)
  }

  private def getUserId(port: CryptoCurrencyTransactionPort, tx: CryptoCurrencyTransaction, txType: TransferType): Option[Long] = {
    if (config.userIdFromMemo && txType == DepositHot) {
      if (port.accountName.isDefined && port.accountName.get.nonEmpty &&
        port.accountName.get.equals(address2AccountNameMap.getOrElse(port.address, ""))) {
        val userId: String = port.memo.getOrElse("0")
        val reg = "1([0-9]{9})".r
        if (reg.pattern.matcher(userId).matches) {
          Some(java.lang.Long.parseLong(userId))
        } else {
          log.error(s"DepositHot tx's output set invalid memo: ${port.toString}, with tx: ${tx.toString}")
          None
        }
      } else {
        log.error(s"DepositHot tx output set incorrect, port: ${port.toString} expect accountName: ${address2AccountNameMap(port.address)}, with tx: ${tx.toString}")
        None
      }
    } else {
      addressUidMap.get(port.address)
    }
  }

  private def getNxtRsAddress(i: CryptoCurrencyTransactionPort): Option[String] = {
    i.nxtRsAddress match {
      case Some(add) => Some(add)
      case _ => address2NxtRsAddressMap.get(i.address)
    }
  }
}
