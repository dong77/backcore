package com.coinport.coinex.transfer

import akka.actor.Actor._
import com.coinport.coinex.data._
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.mongodb.casbah.Imports._
import scala.collection.mutable.{ Map, ListBuffer }

import TransferStatus._
import TransferType._
import akka.actor.ActorLogging

trait AccountTransferBehavior {

  val db: MongoDB
  val manager: AccountTransferManager

  var confirmableHeight: Long = 6L
  val succeededRetainHeight: Long = 200L
  var transferDebug: Boolean = false

  // message need to send to processors
  private val messageBox = ListBuffer.empty[CryptoCurrencyTransferItem]
  //message need to write to mongo
  private val mongoWriteList = ListBuffer.empty[CryptoCurrencyTransferItem]

  def setConfirmableHeight(heightConfig: Int) = { confirmableHeight = heightConfig }

  def setTransferDebug(transferDebugConfig: Boolean) = { this.transferDebug = transferDebugConfig }

  def isCryptoCurrency(currency: Currency): Boolean = { currency.value >= Currency.Btc.value }

  def getMessagesBox = messageBox

  def getMongoWriteList = mongoWriteList

  private def clearResList() = {
    mongoWriteList.clear()
    messageBox.clear()
  }

  def updateState: Receive = {

    case DoRequestTransfer(t) =>
      if (isCryptoCurrency(t.currency) && !transferDebug) {
        t.`type` match {
          case TransferType.Deposit => //Do nothing
          case TransferType.Withdrawal =>
            transferHandler.put(t)
          case _ => //Just handle other type
        }
      } else {
        transferHandler.put(t)
      }
      manager.setLastTransferId(t.id)

    case AdminConfirmTransferFailure(t, _) => transferHandler.put(t)

    case AdminConfirmTransferSuccess(t) => {
      if (isCryptoCurrency(t.currency) && !transferDebug) {
        t.`type` match {
          case TransferType.Deposit =>
          case TransferType.Withdrawal =>
            clearResList
            val to = CryptoCurrencyTransactionPort(t.address.get, None, Some(t.amount), Some(t.userId))
            // id, sigId, txid, userId, currency, from, to(external address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
            val withdrawItem = CryptoCurrencyTransferItem(Some(manager.getNewTransferItemId), None, None, Some(t.userId), Some(t.currency), None, Some(to), None, Some(Withdrawal), Some(Confirming), None, Some(t.id), Some(System.currentTimeMillis()))
            setResState(Updator.copy(item = withdrawItem, addMsgBox = true, putItem = true)) //send message to bitway
          case _ => // Just handle other type, do nothing
        }
      }
      transferHandler.put(t)
    }

    case m @ MultiCryptoCurrencyTransactionMessage(currency, txs, newIndex: Option[BlockIndex]) =>
      // println(s">>>>>>>>>>>>>>>>>>>>> updateState  => MultiCryptoCurrencyTransactionMessage = ${m.toString}")
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
            case None =>
            case _ =>
          }
      }
      handleNeedConfirmTransfer(currency)
    case _ =>
  }

  private def refreshLastBlockHeight(tx: CryptoCurrencyTransaction) {
    val txHeight: Long = if (tx.includedBlock.isDefined) tx.includedBlock.get.height.getOrElse(0L) else 0L
    if (manager.getLastBlockHeight < txHeight) manager.setLastBlockHeight(txHeight)
  }

  private def splitAndHandleTxs(currency: Currency, tx: CryptoCurrencyTransaction) {
    tx.txType.get match {
      case Deposit =>
        tx.outputs.get foreach {
          //every output corresponds to one tx
          outputPort =>
            if (outputPort.userId.isDefined) {
              tx.status match {
                case Failed =>
                  manager.getDepositTxId(tx.sigId.get, outputPort) match {
                    case Some(id) =>
                      val depositItem = manager.transferMap(id).copy(status = Some(Failed))
                      manager.removeDepositTxid(depositItem.sigId.get, depositItem.to.get)
                      setAccountTransferStatus(manager.transferMap, id, Failed)
                      setResState(Updator.copy(item = depositItem, addMongo = true, putItem = true))
                    case _ =>
                  }
                case _ =>
                  val toSaveDepositItem =
                    manager.getDepositTxId(tx.sigId.get, outputPort) match {
                      case Some(id) =>
                        manager.transferMap(id).copy(includedBlock = tx.includedBlock)
                      case None =>
                        val transferId = manager.getTransferId
                        manager.setLastTransferId(transferId)
                        transferHandler.put(AccountTransfer(transferId, outputPort.userId.get, TransferType.Deposit, currency, outputPort.internalAmount.get, Confirming, Some(System.currentTimeMillis())))
                        val newTransferItemId = manager.getNewTransferItemId
                        manager.saveDepositTxId(tx.sigId.get, outputPort, newTransferItemId)
                        // id, sigId, txid, userId, currency, from, to(user's internal address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
                        CryptoCurrencyTransferItem(Some(newTransferItemId), tx.sigId, tx.txid, outputPort.userId, Some(currency), None, Some(outputPort), tx.includedBlock, tx.txType, Some(Confirming), None, Some(transferId), Some(System.currentTimeMillis()))
                    }
                  setResState(Updator.copy(item = toSaveDepositItem, addMongo = true, putItem = true))
              }
            }
        }
      case UserToHot =>
        tx.ids.get foreach {
          id =>
            assert(manager.transferMap.contains(id))
            val userToHotItem = tx.status match {
              case Failed => manager.transferMap(id).copy(status = Some(Failed))
              case _ => manager.transferMap(id).copy(status = Some(Succeeded)) // need only one tx
            }
            setResState(Updator.copy(item = userToHotItem, addMongo = true, rmItem = true))
            userToHotItem.userToHotMapedDepositId foreach {
              depositId =>
                setAccountTransferStatus(manager.transferMap, depositId, userToHotItem.status.get)
                val depositItem = manager.transferMap(depositId).copy(status = Some(userToHotItem.status.get))
                manager.removeDepositTxid(depositItem.sigId.get, depositItem.to.get)
                setResState(Updator.copy(item = depositItem, addMongo = true, addMsgBox = depositItem.status == Succeeded, rmItem = true))
            }
        }
      case Withdrawal =>
        tx.ids.get foreach {
          //every input corresponds to one tx
          id =>
            assert(manager.transferMap.contains(id))
            tx.status match {
              case Failed =>
                setAccountTransferStatus(manager.transferMap, id, Failed)
                setResState(Updator.copy(item = manager.transferMap(id).copy(status = Some(Failed)), addMongo = true, addMsgBox = true, rmItem = true))
              case _ =>
                val withdrawItem = manager.transferMap(id).copy(sigId = tx.sigId, txid = tx.txid, includedBlock = tx.includedBlock)
                setAccountTransferStatus(manager.transferMap, id, Confirming)
                setResState(Updator.copy(item = withdrawItem, addMongo = true, putItem = true))
            }
        }
      case _ =>
    }
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
    } /* else {
      log.error(s"Failed to get item for id ${itemId} to update to status ${status.toString}")
    }*/
  }

  // handle transfers that not confirmed by height
  private def handleNeedConfirmTransfer(currency: Currency) {
    val lastBlockHeight = manager.getLastBlockHeight
    manager.transferMap.values foreach {
      item =>
        item.txType.get match {
          case Deposit if item.includedBlock.isDefined && item.status != Confirmed && item.status != Reorging => //Reorging item will not confirm again to avoid resend UserToHot message
            if (lastBlockHeight - item.includedBlock.get.height.getOrElse(Long.MaxValue) >= confirmableHeight) {
              setResState(Updator.copy(item = item.copy(status = Some(Confirmed)), addMongo = true, putItem = true))
              val user2HotItem =
                // id, sigId, txid, userId, currency, from, to(hot address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
                CryptoCurrencyTransferItem(Some(manager.getNewTransferItemId), None, None, item.userId, Some(currency), item.to, None, None, Some(UserToHot), Some(Confirming), item.id, None, Some(System.currentTimeMillis()))
              setResState(Updator.copy(item = user2HotItem, addMongo = true, addMsgBox = true, putItem = true))
            } else {
              updateAccountTransferConfirmNum(item, lastBlockHeight)
            }
          case Withdrawal if item.includedBlock.isDefined =>
            if (lastBlockHeight - item.includedBlock.get.height.getOrElse(Long.MaxValue) >= confirmableHeight) {
              setResState(Updator.copy(item = item.copy(status = Some(Succeeded)), addMongo = true, addMsgBox = true, rmItem = true))
            } else {
              updateAccountTransferConfirmNum(item, lastBlockHeight)
            }
          case _ =>
        }
    }
    manager.succeededMap.values foreach {
      item =>
        if (lastBlockHeight - item.includedBlock.get.height.get > succeededRetainHeight) {
          manager.succeededMap.remove(item.id.get)
        }
    }
  }

  private def updateAccountTransferConfirmNum(item: CryptoCurrencyTransferItem, lastBlockHeight: Long) {
    if (item.includedBlock.isDefined && item.includedBlock.get.height.isDefined && item.includedBlock.get.height.get > lastBlockHeight) {
      item.accountTransferId foreach {
        accountTransferId =>
          transferHandler.get(accountTransferId) foreach {
            transfer =>
              transferHandler.put(transfer.copy(confirm = Some(lastBlockHeight - item.includedBlock.get.height.get), updated = Some(System.currentTimeMillis())))
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
            //Success, Confirmed, Reorging
            val reOrgItem = item.copy(includedBlock = Some(reOrgBlock), status = Some(Reorging))
            setResState(Updator.copy(item = reOrgItem, addMongo = true, putItem = true))
          }

          item.status match {
            case Some(Confirming) if reOrgBlock.height.get < itemHeight =>
              val confirmingItem: CryptoCurrencyTransferItem = item.copy(includedBlock = Some(reOrgBlock))
              setResState(Updator.copy(item = confirmingItem, addMongo = true, putItem = true))
            case Some(Confirmed) if reOrgBlock.height.get - itemHeight < confirmableHeight =>
              setReorg(item)
            case Some(Reorging) if reOrgBlock.height.get < itemHeight =>
              setReorg(item)
            case Some(Succeeded) => //Succeeded item has mv to manager.succeededMap, no need to reorging
            case None =>
            case _ =>
          }
        }
    }
    manager.succeededMap.keys foreach { // reorging succeeded item
      key: Long =>
        val item = manager.succeededMap(key)
        if (reOrgBlock.height.get - item.includedBlock.get.height.get < confirmableHeight) {
          setAccountTransferStatus(manager.succeededMap, key, Reorging)
          manager.succeededMap.remove(key) //no need to reserve reorging item
        }
    }
  }

  private def setResState(up: Updator) {
    if (up.addMongo) mongoWriteList.append(up.item)
    if (up.addMsgBox) messageBox.append(up.item)
    if (up.rmItem) {
      manager.transferMap.remove(up.item.id.get)
      if (up.item.status == Succeeded) {
        up.item.txType.get match {
          case Deposit => manager.succeededMap.put(up.item.id.get, up.item)
          case Withdrawal => manager.succeededMap.put(up.item.id.get, up.item)
          case _ =>
        }
      }
    }
    if (up.putItem) manager.transferMap.put(up.item.id.get, up.item.copy(updated = Some(System.currentTimeMillis())))
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
    def extractId(item: CryptoCurrencyTransferItem) = item.id.get

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
