package com.coinport.coinex.transfer

import akka.event.LoggingAdapter
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.data._
import com.coinport.coinex.data.TransferStatus._
import com.coinport.coinex.data.TransferType._

trait CryptoCurrencyTransferHandler {
  var manager: AccountTransferManager = null
  var transferHandler: SimpleJsonMongoCollection[AccountTransfer, AccountTransfer.Immutable] = null
  var transferItemHandler: SimpleJsonMongoCollection[CryptoCurrencyTransferItem, CryptoCurrencyTransferItem.Immutable] = null
  var logger: LoggingAdapter = null
  val defaultConfirmNum: Int = 1
  var succeededRetainNum = collection.immutable.Map.empty[Currency, Int]
  private val defaultSucceededRetainNum = 100
  var item: CryptoCurrencyTransferItem = null
  private var innerConfirmNum: Option[Int] = None
  private var innerTimestamp: Option[Long] = None

  def setEnv(env: TransferEnv, timestamp: Option[Long]) {
    manager = env.manager
    transferHandler = env.transferHandler
    transferItemHandler = env.transferItemHandler
    logger = env.logger
    succeededRetainNum = env.succeededRetainNum
    setTimeStamp(timestamp)
  }

  def onNormal(tx: CryptoCurrencyTransaction) {
    item.includedBlock match {
      case Some(_) =>
      case None =>
        item = item.copy(sigId = tx.sigId, txid = tx.txid, includedBlock = tx.includedBlock, status = Some(Confirming), updated = getTimestamp, minerFee = tx.minerFee)
        setAccountTransferStatus(Confirming)
        saveItemToMongo()
    }
  }

  def onSucceeded() {
    item = item.copy(status = Some(Succeeded), updated = getTimestamp())
    setAccountTransferStatus(Succeeded)
    saveItemToMongo()
  }

  def onFail(failStatus: TransferStatus = Failed) {
    item = item.copy(status = Some(failStatus), updated = getTimestamp())
    setAccountTransferStatus(failStatus)
    saveItemToMongo()
  }

  def checkConfirm(lastBlockHeight: Long): Boolean = {
    if (item.includedBlock.isDefined && item.status.get != Succeeded && item.status.get != Confirmed) {
      val confirmed = lastBlockHeight - item.includedBlock.get.height.getOrElse(Long.MaxValue) >= itemComfirmNum - 1
      if (confirmed) {
        val statusUpdate = if (item.txType.get != Deposit) Succeeded else Confirmed
        item = item.copy(status = Some(statusUpdate))
        setAccountTransferStatus(statusUpdate)
        saveItemToMongo()
      }
      updateAccountTransferConfirmNum(lastBlockHeight)
      confirmed
    } else {
      false
    }
  }

  def checkRemoveSucceeded(lastBlockHeight: Long): Boolean = {
    item.status.get == Succeeded && (lastBlockHeight - item.includedBlock.get.height.get) > itemSucceededRetainHeight
  }

  def reOrgnize(reOrgHeight: Long) {
    if (item.includedBlock.isDefined && item.includedBlock.get.height.isDefined) {
      val itemHeight = item.includedBlock.get.height.get

      // reset item which has bigger height than reOrg's height
      def setReorg() {
        // Confirmed, Reorging
        val newBlock = if (reOrgHeight < itemHeight) None else item.includedBlock
        item = item.copy(includedBlock = newBlock, status = Some(Reorging))
        saveItemToMongo()
      }

      item.status match {
        case Some(Confirming) if reOrgHeight < itemHeight =>
          logger.warning(s"reOrgnize() reOrgnize happened(Confirming) :item -> ${item.toString()}")
          item = item.copy(includedBlock = None)
          saveItemToMongo()
        case Some(Confirmed) if reOrgHeight - itemHeight < itemComfirmNum - 1 =>
          logger.warning(s"reOrgnize() reOrgnize happened(Confirmed) :item -> ${item.toString()}")
          setReorg()
        case Some(Reorging) if reOrgHeight < itemHeight =>
          logger.warning(s"reOrgnize() reOrgnize happened(Reorging) :item -> ${item.toString()}")
          setReorg()
        case Some(Succeeded) => //Succeeded item has mv to manager.succeededMap, no need to reorging
        case None =>
        case _ =>
      }

    }
  }

  def reOrgnizeSucceeded(reOrgHeight: Long): Boolean = {
    if (reOrgHeight - item.includedBlock.get.height.get < itemComfirmNum - 1) {
      logger.warning(s"reOrgnize() reOrgnize happened(Succeeded) :item -> ${item.toString()}")
      setAccountTransferStatus(Reorging)
      return true
    }
    false
  }

  def setTimeStamp(timestamp: Option[Long]): CryptoCurrencyTransferHandler = {
    this.innerTimestamp = Some(timestamp.getOrElse(System.currentTimeMillis()))
    this
  }

  def getTimestamp(): Option[Long] = {
    innerTimestamp match {
      case Some(_) => innerTimestamp
      case _ => Some(System.currentTimeMillis())
    }
  }

  def setConfirmNum(confirmNum: Option[Int]): CryptoCurrencyTransferHandler = {
    this.innerConfirmNum = Some(confirmNum.getOrElse(defaultConfirmNum))
    this
  }

  protected def saveItemToMongo() {
    logger.info("saveItemToMongo : " + item.toString)
    transferItemHandler.put(item.copy(updated = getTimestamp()))
  }

  private def setAccountTransferStatus(status: TransferStatus) {
    item.accountTransferId foreach {
      accountTransferId =>
        transferHandler.get(accountTransferId) foreach {
          transfer =>
            transferHandler.put(transfer.copy(status = status, updated = getTimestamp(), txid = item.txid))
        }
    }
  }

  private def updateAccountTransferConfirmNum(lastBlockHeight: Long) {
    if (item.includedBlock.isDefined && item.includedBlock.get.height.isDefined && lastBlockHeight >= item.includedBlock.get.height.get) {
      item.accountTransferId foreach {
        accountTransferId =>
          transferHandler.get(accountTransferId) foreach {
            transfer =>
              transferHandler.put(transfer.copy(confirm = Some(lastBlockHeight - item.includedBlock.get.height.get + 1), updated = getTimestamp(), txid = item.txid))
          }
      }
    }
  }

  private def itemComfirmNum(): Int = {
    innerConfirmNum.getOrElse(defaultConfirmNum)
  }

  private def itemSucceededRetainHeight(): Int = {
    succeededRetainNum.getOrElse(item.currency, defaultSucceededRetainNum)
  }

}
