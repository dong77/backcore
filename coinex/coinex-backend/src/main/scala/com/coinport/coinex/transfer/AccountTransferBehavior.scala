package com.coinport.coinex.transfer

import akka.actor.Actor._
import akka.event.LoggingAdapter
import com.coinport.coinex.data._
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.mongodb.casbah.Imports._
import scala.collection.mutable.{ Map, ListBuffer }

import TransferStatus._
import TransferType._

trait AccountTransferBehavior {

  val db: MongoDB
  val manager: AccountTransferManager

  implicit val logger: LoggingAdapter

  var confirmableHeight: Long = 6L
  val succeededRetainHeight: Long = 200L
  var transferDebug: Boolean = false
  val internalUserId: Int = 0

  // message need to send to processors
  private val messageBox = ListBuffer.empty[CryptoCurrencyTransferItem]
  //message need to write to mongo
  private val mongoWriteList = ListBuffer.empty[CryptoCurrencyTransferItem]

  def setConfirmableHeight(heightConfig: Int) = {
    confirmableHeight = heightConfig
  }

  def setTransferDebug(transferDebugConfig: Boolean) = {
    transferDebug = transferDebugConfig
  }

  def isCryptoCurrency(currency: Currency): Boolean = {
    currency.value >= Currency.Btc.value
  }

  def getMessagesBox = messageBox

  def getMongoWriteList = mongoWriteList

  private def clearResList() = {
    mongoWriteList.clear()
    messageBox.clear()
  }

  def updateState: Receive = {

    case DoRequestTransfer(t) =>
      if (isCryptoCurrency(t.currency) && !transferDebug) {
        clearResList
        t.`type` match {
          case TransferType.Deposit => //Do nothing
          case TransferType.UserToHot =>
          case TransferType.Withdrawal =>
            transferHandler.put(t)
          case TransferType.ColdToHot => //Just log, will confirmed by admin
            transferHandler.put(t)
          case TransferType.HotToCold =>
            transferHandler.put(t)
            val from = CryptoCurrencyTransactionPort("", None, Some(t.amount), Some(t.userId))
            val to = CryptoCurrencyTransactionPort("", None, Some(t.amount), Some(t.userId))
            prepareItemSendToBitway(t, Some(from), Some(to))
          case TransferType.Unknown =>
            transferHandler.put(t)
        }
      } else {
        transferHandler.put(t)
      }
      manager.setLastTransferId(t.id)

    case AdminConfirmTransferFailure(t, _) => transferHandler.put(t)

    case DoCancelTransfer(t) => transferHandler.put(t)

    case AdminConfirmTransferSuccess(t) => {
      if (isCryptoCurrency(t.currency) && !transferDebug) {
        clearResList
        t.`type` match {
          case TransferType.Withdrawal =>
            val to = CryptoCurrencyTransactionPort(t.address.get, None, Some(t.amount), Some(t.userId))
            prepareItemSendToBitway(t, None, Some(to))
          case _ => // Just handle other type, do nothing
        }
      }
      transferHandler.put(t)
    }

    case m @ MultiCryptoCurrencyTransactionMessage(currency, txs, newIndex: Option[BlockIndex]) =>
      logger.info(s">>>>>>>>>>>>>>>>>>>>> updateState  => MultiCryptoCurrencyTransactionMessage = ${m.toString}")
      clearResList
      if (manager.getLastBlockHeight > 0) newIndex foreach (index => reOrgnize(index))
      txs foreach {
        tx =>
          refreshLastBlockHeight(tx)
          tx.txType match {
            case Some(Deposit) =>
              splitAndHandleTxs(currency, tx)
            case Some(UserToHot) =>
              splitAndHandleTxs(currency, tx)
            case Some(Withdrawal) =>
              splitAndHandleTxs(currency, tx)
            case Some(ColdToHot) =>
              splitAndHandleTxs(currency, tx)
            case Some(HotToCold) =>
              splitAndHandleTxs(currency, tx)
            case Some(Unknown) =>
              logger.warning(s"Unknown tx meet : ${tx.toString}")
            case _ =>
              logger.warning(s"Unexpected tx meet : ${tx.toString}")
          }
      }
      handleNeedConfirmTransfer(currency)
    case _ =>
  }

  private def prepareItemSendToBitway(t: AccountTransfer, from: Option[CryptoCurrencyTransactionPort], to: Option[CryptoCurrencyTransactionPort]) {
    clearResList
    // id, currency, sigId, txid, userId, from, to(external address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
    val cryptoCurrencyTransferItem = CryptoCurrencyTransferItem(manager.getNewTransferItemId, t.currency, None, None, Some(t.userId), from, to, None, Some(t.`type`), Some(Confirming), None, Some(t.id), Some(System.currentTimeMillis()))
    setResState(Updator.copy(item = cryptoCurrencyTransferItem, addMsgBox = true, addMongo = true, putItem = true)) //send message to bitway
  }

  private def refreshLastBlockHeight(tx: CryptoCurrencyTransaction) {
    val txHeight: Long = if (tx.includedBlock.isDefined) tx.includedBlock.get.height.getOrElse(0L) else 0L
    if (manager.getLastBlockHeight < txHeight) manager.setLastBlockHeight(txHeight)
  }

  private def splitAndHandleTxs(currency: Currency, tx: CryptoCurrencyTransaction) {
    tx.txType.get match {
      case Deposit =>
        handleDepositLikeTx(currency, tx, manager.depositSigId2TxPortIdMap)
      case UserToHot =>
        tx.ids match {
          case Some(_) =>
            tx.ids.get foreach {
              id =>
                if (manager.transferMap.contains(id)) {
                  val userToHotItem = tx.status match {
                    case Failed =>
                      setAccountTransferStatus(manager.transferMap, id, Failed)
                      manager.transferMap(id).copy(sigId = tx.sigId, txid = tx.txid, status = Some(Failed))
                    case _ => manager.transferMap(id).includedBlock match {
                      case Some(_) => manager.transferMap(id).copy(sigId = tx.sigId, txid = tx.txid)
                      case None => manager.transferMap(id).copy(sigId = tx.sigId, txid = tx.txid, includedBlock = tx.includedBlock)
                    } // need only one tx
                  }
                  setResState(Updator.copy(item = userToHotItem, addMongo = true, putItem = true))
                  userToHotItem.userToHotMapedDepositId match {
                    case Some(depositId) =>
                      if (manager.transferMap.contains(depositId)) {
                        // check coresponded deposit item has not been removed from map
                        val depositStatus = if (userToHotItem.status.get != Failed) Succeeded else Failed
                        setAccountTransferStatus(manager.transferMap, depositId, depositStatus)
                        val depositItem = manager.transferMap(depositId).copy(status = Some(depositStatus))
                        manager.removeItemIdFromMap(manager.depositSigId2TxPortIdMap, depositItem.sigId.get, depositItem.to.get)
                        setResState(Updator.copy(item = depositItem, addMongo = true, addMsgBox = (depositItem.status.get == Succeeded), rmItem = true))
                      }
                    case None =>
                      logger.warning(s"splitAndHandleTxs() UserToHot item not define userToHotMapedDepositId : ${userToHotItem.toString}")
                  }
                } else {
                  logger.warning(s"splitAndHandleTxs() UserToHot item confirm id not include in transferMap : ${tx.toString}")
                }
            }
            updateSigId2MinerFee(tx)
          case None =>
            logger.warning(s"splitAndHandleTxs() UserToHot tx not define ids : ${tx.toString}")
        }
      case Withdrawal =>
        handleWithdrawlLikeTx(tx)
      case ColdToHot =>
        handleDepositLikeTx(currency, tx, manager.coldToHotSigId2TxPortIdMap)
      case HotToCold =>
        handleWithdrawlLikeTx(tx)
      case _ =>
    }
  }

  def handleDepositLikeTx(currency: Currency, tx: CryptoCurrencyTransaction, sigIdToItemMap: Map[String, Map[CryptoCurrencyTransactionPort, Long]]) {
    tx.outputs match {
      case Some(_) =>
        tx.outputs.get foreach {
          //every output corresponds to one tx
          outputPort =>
            if (outputPort.userId.isDefined) {
              tx.status match {
                case Failed =>
                  manager.getItemIdFromMap(sigIdToItemMap, tx.sigId.get, outputPort) match {
                    case Some(id) =>
                      val depositLikeItem = manager.transferMap(id).copy(status = Some(Failed))
                      manager.removeItemIdFromMap(sigIdToItemMap, depositLikeItem.sigId.get, depositLikeItem.to.get)
                      setAccountTransferStatus(manager.transferMap, id, Failed)
                      setResState(Updator.copy(item = depositLikeItem, addMongo = true, putItem = true))
                    case _ =>
                  }
                case _ =>
                  val toSaveDepositLikeItem =
                    manager.getItemIdFromMap(sigIdToItemMap, tx.sigId.get, outputPort) match {
                      case Some(id) =>
                        manager.transferMap(id).includedBlock match {
                          case Some(_) => manager.transferMap(id)
                          case None => manager.transferMap(id).copy(includedBlock = tx.includedBlock)
                        }
                      case None =>
                        val transferId = manager.getTransferId
                        manager.setLastTransferId(transferId)
                        transferHandler.put(AccountTransfer(transferId, outputPort.userId.get, tx.txType.get, currency, outputPort.internalAmount.get, Confirming, Some(System.currentTimeMillis())))
                        val newTransferItemId = manager.getNewTransferItemId
                        manager.saveItemIdToMap(sigIdToItemMap, tx.sigId.get, outputPort, newTransferItemId)
                        // id, currency, sigId, txid, userId, from, to(user's internal address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
                        CryptoCurrencyTransferItem(newTransferItemId, currency, tx.sigId, tx.txid, outputPort.userId, None, Some(outputPort), tx.includedBlock, tx.txType, Some(Confirming), None, Some(transferId), Some(System.currentTimeMillis()))
                    }
                  setResState(Updator.copy(item = toSaveDepositLikeItem, addMongo = true, putItem = true))
              }
            }
        }
        updateSigId2MinerFee(tx)
      case None =>
        logger.warning(s"handleDepositLikeTx() ${tx.txType.get.toString} tx not define outputs : ${tx.toString}")
    }
  }

  private def handleWithdrawlLikeTx(tx: CryptoCurrencyTransaction) {
    tx.ids match {
      case Some(_) =>
        tx.ids.get foreach {
          //every input corresponds to one tx
          id =>
            if (manager.transferMap.contains(id)) {
              tx.status match {
                case Failed =>
                  setAccountTransferStatus(manager.transferMap, id, Failed)
                  setResState(Updator.copy(item = manager.transferMap(id).copy(status = Some(Failed)), addMongo = true, addMsgBox = true, rmItem = true))
                case _ =>
                  val item = manager.transferMap(id).includedBlock match {
                    case Some(_) => manager.transferMap(id).copy(sigId = tx.sigId, txid = tx.txid, status = Some(Confirming))
                    case None => manager.transferMap(id).copy(sigId = tx.sigId, txid = tx.txid, status = Some(Confirming), includedBlock = tx.includedBlock)
                  }
                  setResState(Updator.copy(item = item, addMongo = true, putItem = true))
              }
            } else {
              logger.warning(s"handleWithdrawlLikeTx() item confirm id not included in transferMap : ${tx.toString}")
            }
        }
        updateSigId2MinerFee(tx)
      case None =>
        logger.warning(s"handleWithdrawlLikeTx() ${tx.txType.get.toString} tx not define ids : ${tx.toString}")
    }
  }

  private def updateSigId2MinerFee(tx: CryptoCurrencyTransaction) {
    def updateMinerFee() {
      tx.minerFee match {
        case Some(fee) =>
          tx.status match {
            case Failed => manager.sigId2MinerFeeMap.remove(tx.sigId.get)
            case _ => manager.sigId2MinerFeeMap.put(tx.sigId.get, fee)
          }
        case None =>
      }
    }

    tx.sigId match {
      case Some(sigId) =>
        tx.txType match {
          case Some(Withdrawal) => updateMinerFee
          case Some(UserToHot) => updateMinerFee
          case Some(ColdToHot) => updateMinerFee
          case Some(HotToCold) => updateMinerFee
          case _ =>
        }
      case None => logger.warning(s"saveSigId2MinerFee() tx not define sigId : ${tx.toString}")
    }

  }

  // handle transfers that not confirmed by height
  private def handleNeedConfirmTransfer(currency: Currency) {
    val lastBlockHeight = manager.getLastBlockHeight
    manager.transferMap.values foreach {
      item =>
        item.txType.get match {
          case Deposit if item.includedBlock.isDefined && item.status.get != Confirmed && item.status.get != Reorging => //Reorging item will not confirm again to avoid resend UserToHot message
            if (checkConfirm(item, lastBlockHeight, false)) {
              val transferId = manager.getTransferId
              manager.setLastTransferId(transferId)
              transferHandler.put(AccountTransfer(transferId, item.userId.get, TransferType.UserToHot, currency, item.to.get.internalAmount.get, Confirming, Some(System.currentTimeMillis())))
              val user2HotItem =
                // id, currency, sigId, txid, userId, from, to(hot address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
                CryptoCurrencyTransferItem(manager.getNewTransferItemId, currency, None, None, item.userId, item.to, None, None, Some(UserToHot), Some(Confirming), Some(item.id), Some(transferId), Some(System.currentTimeMillis()))
              setResState(Updator.copy(item = user2HotItem, addMongo = true, addMsgBox = true, putItem = true))
            }
          case UserToHot if item.includedBlock.isDefined =>
            checkConfirm(item, lastBlockHeight)
          case Withdrawal if item.includedBlock.isDefined =>
            checkConfirm(item, lastBlockHeight)
          case ColdToHot if item.includedBlock.isDefined =>
            checkConfirm(item, lastBlockHeight)
          case HotToCold if item.includedBlock.isDefined =>
            checkConfirm(item, lastBlockHeight)
          case _ =>
        }
    }
    manager.succeededMap.values foreach {
      item =>
        if (lastBlockHeight - item.includedBlock.get.height.get > succeededRetainHeight) {
          manager.succeededMap.remove(item.id)
        }
    }
  }

  private def checkConfirm(item: CryptoCurrencyTransferItem, lastBlockHeight: Long, notDeposit: Boolean = true): Boolean = {
    val confirmed = lastBlockHeight - item.includedBlock.get.height.getOrElse(Long.MaxValue) >= confirmableHeight - 1
    if (confirmed) {
      val statusUpdate = if (notDeposit) Succeeded else Confirmed
      setAccountTransferStatus(manager.transferMap, item.id, statusUpdate)
      setResState(Updator.copy(item = item.copy(status = Some(statusUpdate)), addMongo = true, addMsgBox = notDeposit, rmItem = notDeposit, putItem = !notDeposit))
      if (item.txType.get == ColdToHot) {
        manager.removeItemIdFromMap(manager.coldToHotSigId2TxPortIdMap, item.sigId.get, item.to.get)
      }
    }
    updateAccountTransferConfirmNum(item, lastBlockHeight)
    confirmed
  }

  private def setAccountTransferStatus(updateMap: Map[Long, CryptoCurrencyTransferItem], itemId: Long, status: TransferStatus) {
    if (updateMap.contains(itemId)) {
      val item = updateMap(itemId)
      item.accountTransferId foreach {
        accountTransferId =>
          transferHandler.get(accountTransferId) foreach {
            transfer =>
              transferHandler.put(transfer.copy(status = status, updated = Some(System.currentTimeMillis())))
          }
      }
    } else {
      logger.warning(s"Failed to get item for id ${itemId} to update to status ${status.toString}")
    }
  }

  private def updateAccountTransferConfirmNum(item: CryptoCurrencyTransferItem, lastBlockHeight: Long) {
    if (item.includedBlock.isDefined && item.includedBlock.get.height.isDefined && lastBlockHeight >= item.includedBlock.get.height.get) {
      item.accountTransferId foreach {
        accountTransferId =>
          transferHandler.get(accountTransferId) foreach {
            transfer =>
              transferHandler.put(transfer.copy(confirm = Some(lastBlockHeight - item.includedBlock.get.height.get + 1), updated = Some(System.currentTimeMillis())))
          }
      }
    }
  }

  private def reOrgnize(reOrgBlock: BlockIndex) {
    assert(reOrgBlock.height.isDefined && reOrgBlock.height.get < manager.getLastBlockHeight)
    manager.transferMap.keys foreach {
      key: Long =>
        val item = manager.transferMap(key)
        if (item.includedBlock.isDefined && item.includedBlock.get.height.isDefined) {
          val itemHeight = item.includedBlock.get.height.get

          // reset item which has bigger height than reOrg's height
          def setReorg(item: CryptoCurrencyTransferItem) {
            // Confirmed, Reorging
            val newBlock = if (reOrgBlock.height.get < itemHeight) None else item.includedBlock
            val reOrgItem = item.copy(includedBlock = newBlock, status = Some(Reorging))
            setResState(Updator.copy(item = reOrgItem, addMongo = true, putItem = true))
          }

          item.status match {
            case Some(Confirming) if reOrgBlock.height.get < itemHeight =>
              val confirmingItem: CryptoCurrencyTransferItem = item.copy(includedBlock = None)
              setResState(Updator.copy(item = confirmingItem, addMongo = true, putItem = true))
            case Some(Confirmed) if reOrgBlock.height.get - itemHeight < confirmableHeight - 1 =>
              setReorg(item)
            case Some(Reorging) if reOrgBlock.height.get < itemHeight =>
              setReorg(item)
            case Some(Succeeded) => //Succeeded item has mv to manager.succeededMap, no need to reorging
            case None =>
            case _ =>
          }
        }
    }
    manager.succeededMap.keys foreach {
      // reorging succeeded item
      key: Long =>
        val item = manager.succeededMap(key)
        if (reOrgBlock.height.get - item.includedBlock.get.height.get < confirmableHeight - 1) {
          setAccountTransferStatus(manager.succeededMap, key, Reorging)
          manager.succeededMap.remove(key) //no need to reserve reorging item
        }
    }
  }

  private def setResState(up: Updator) {
    if (up.addMongo) mongoWriteList.append(up.item)
    if (up.addMsgBox) messageBox.append(up.item)
    if (up.rmItem) {
      manager.transferMap.remove(up.item.id)
      if (up.item.status.get == Succeeded) {
        manager.succeededMap.put(up.item.id, up.item)
      }
    }
    if (up.putItem) manager.transferMap.put(up.item.id, up.item.copy(updated = Some(System.currentTimeMillis())))
  }

  val transferHandler = new SimpleJsonMongoCollection[AccountTransfer, AccountTransfer.Immutable]() {
    lazy val coll = db("transfers")

    def extractId(item: AccountTransfer) = item.id

    def getQueryDBObject(q: QueryTransfer): MongoDBObject = {
      var query = MongoDBObject()
      if (q.uid.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.UserIdField.name -> q.uid.get)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.CurrencyField.name -> q.currency.get.name)
      if (q.`type`.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.TypeField.name -> q.`type`.get.toString)
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.StatusField.name -> q.status.get.name)
      if (q.spanCur.isDefined) query ++= (DATA + "." + AccountTransfer.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }

  val transferItemHandler = new SimpleJsonMongoCollection[CryptoCurrencyTransferItem, CryptoCurrencyTransferItem.Immutable]() {
    lazy val coll = db("transferitems")
    def extractId(item: CryptoCurrencyTransferItem) = item.id

    def getQueryDBObject(q: QueryCryptoCurrencyTransfer): MongoDBObject = {
      var query = MongoDBObject()
      if (q.id.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.IdField.name -> q.id.get)
      if (q.sigId.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.SigIdField.name -> q.sigId.get)
      if (q.txid.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.TxidField.name -> q.txid.get.toString)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.CurrencyField.name -> q.currency.get.name)
      if (q.txType.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.TxTypeField.name -> q.txType.get.name)
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.StatusField.name -> q.status.get.name)
      if (q.spanCur.isDefined) query ++= (DATA + "." + CryptoCurrencyTransferItem.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }
}

case class Updator(item: CryptoCurrencyTransferItem, addMongo: Boolean, addMsgBox: Boolean, rmItem: Boolean, putItem: Boolean)

object Updator {
  val item: CryptoCurrencyTransferItem = null
  val addMongo: Boolean = false
  val addMsgBox: Boolean = false
  val rmItem: Boolean = false
  val putItem: Boolean = false
  def copy(
    item: CryptoCurrencyTransferItem = this.item,
    addMongo: Boolean = this.addMongo,
    addMsgBox: Boolean = this.addMsgBox,
    rmItem: Boolean = this.rmItem,
    putItem: Boolean = this.putItem): Updator = {
    Updator(item, addMongo, addMsgBox, rmItem, putItem)
  }
}
