package com.coinport.coinex.transfer

import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import akka.actor.Actor._
import scala.Some

trait CryptoCurrencyTransferBehavior {
  val transferMap = collection.mutable.Map.empty[Long, CryptoCurrencyTransferItem]
  val db: MongoDB
  val manager: CryptoCurrencyTransferManager
  var confirmableHeight: Int = 6
  private val messageHanleResList = collection.mutable.ListBuffer.empty[CryptoCurrencyTransferItem]

  def setConfirmableHeight(heightConfig: Int) = confirmableHeight = heightConfig

  private def clearResList() = messageHanleResList.clear()

  def updateState: Receive = {
    //    case DoRequestTransfer(t) =>
    //      cryptoCurrencyTransferHandler.put(t)
    //      manager.setLastTransferId(t.id)
    //    case AdminConfirmTransferSuccess(t) => cryptoCurrencyTransferHandler.put(t)
    //    case AdminConfirmTransferFailure(t, _) => cryptoCurrencyTransferHandler.put(t)

    case MultiCryptoCurrencyTransactionMessage(currency, txs, newIndex: Option[BlockIndex]) =>
      clearResList
      newIndex foreach (index => reOrgnize(index))
      txs foreach {
        tx =>
          refreshLastBlockHeight(tx)
          tx.txType.get match {
            case CryptoCurrencyTransactionType.Deposit =>
              splitAndPutTx(currency, tx)
            case CryptoCurrencyTransactionType.UserToHot =>
              splitAndPutTx(currency, tx)
            case CryptoCurrencyTransactionType.Withdrawal =>
          }
      }
      handleNeedConfirmTransfer(currency)
    case _ =>
  }

  def getResList = messageHanleResList

  // handle transfers that not confirmed by height
  private def handleNeedConfirmTransfer(currency: Currency) {
    val lastBlockHeight = manager.getLastBlockHeight
    transferMap.values foreach {
      item =>
        item.txType.get match {
          case CryptoCurrencyTransactionType.Deposit =>
            if (item.includedBlock.isDefined && lastBlockHeight - item.includedBlock.get.height.getOrElse(Long.MaxValue) > confirmableHeight) {
              val resItem = item.copy(status = Some(CryptoCurrencyTransactionStatus.Confirmed))
              transferMap.put(item.id.get, resItem)
              messageHanleResList.append(resItem)
              val user2HotId = manager.getNewTransferId
              val user2HotItem =
                // id, sigId, txid, currency, input(user's address in user2hot), output, includedBlock, txType, status
                CryptoCurrencyTransferItem(Some(user2HotId), None, None, Some(currency), item.to, None, None, Some(CryptoCurrencyTransactionType.UserToHot), Some(CryptoCurrencyTransactionStatus.Pending), item.id)
              transferMap.put(user2HotId, user2HotItem)
              messageHanleResList.append(resItem)
            }
          case CryptoCurrencyTransactionType.UserToHot =>
            if (item.includedBlock.isDefined && lastBlockHeight - item.includedBlock.get.height.getOrElse(Long.MaxValue) >= 1) {
              val resItem = item.copy(status = Some(CryptoCurrencyTransactionStatus.Success))
              transferMap.remove(item.id.get)
              messageHanleResList.append(resItem)
              val mapedDepositItem = transferMap(item.userToHotMapedDepositId.get).copy(status = Some(CryptoCurrencyTransactionStatus.Success))
              transferMap.remove(item.userToHotMapedDepositId.get)
              messageHanleResList.append(mapedDepositItem)
            }
        }
    }
  }

  private def splitAndPutTx(currency: Currency, tx: CryptoCurrencyTransaction) {
    tx.txType.get match {
      case CryptoCurrencyTransactionType.Deposit =>
        tx.outputs.get foreach {
          //every output corresponds to one tx
          outputPort =>
            val toSaveItem =
              manager.getDepositTxId(tx.sigId.get, outputPort) match {
                case Some(id) =>
                  transferMap(id).copy(includedBlock = tx.includedBlock)
                case None =>
                  val newTransferId = manager.getNewTransferId
                  manager.saveDepositTxId(tx.sigId.get, outputPort, newTransferId)
                  // id, sigId, txid, currency, input, output(user's address in coinex), includedBlock, txType, status
                  CryptoCurrencyTransferItem(Some(newTransferId), tx.sigId, tx.txid, Some(currency), None, Some(outputPort), tx.includedBlock, tx.txType, Some(CryptoCurrencyTransactionStatus.Pending))
              }
            transferMap.put(toSaveItem.id.get, toSaveItem)
            messageHanleResList.append(toSaveItem)
        }
      case CryptoCurrencyTransactionType.UserToHot =>
        tx.ids.get foreach {
          //every input corresponds to one tx
          id =>
            assert(transferMap.contains(id))
            tx.status match {
              case CryptoCurrencyTransactionStatus.Failed =>
                val user2HotItemFailed = transferMap(id).copy(status = Some(tx.status))
                transferMap.remove(id)
                messageHanleResList.append(user2HotItemFailed)
                user2HotItemFailed.userToHotMapedDepositId foreach {
                  depositId =>
                    transferMap.remove(depositId)
                    messageHanleResList.append(transferMap(depositId).copy(status = Some(tx.status)))
                }
              case _ =>
                val user2HotItem = transferMap(id).copy(sigId = tx.sigId, txid = tx.txid, includedBlock = tx.includedBlock)
                transferMap.put(id, user2HotItem)
                messageHanleResList.append(user2HotItem)
            }
        }
    }
  }

  private def reOrgnize(reOrgBlock: BlockIndex) {
    assert(reOrgBlock.height.isDefined && reOrgBlock.height.get < manager.getLastBlockHeight)
    transferMap.keys foreach {
      key: Long =>
        val item = transferMap(key)
        if (item.includedBlock.isDefined && item.includedBlock.get.height.isDefined) {
          // ignore height not set condition
          val itemHeight = item.includedBlock.get.height.get
          if (itemHeight > reOrgBlock.height.get) {

            // reset item which has bigger height than reOrg's height
            def setReorg(item: CryptoCurrencyTransferItem) {
              //Success, Confirmed, Reorging
              val reOrgItem = item.copy(includedBlock = Some(reOrgBlock), status = Some(CryptoCurrencyTransactionStatus.Reorging))
              transferMap.put(key, reOrgItem)
              messageHanleResList.append(reOrgItem)
            }

            item.status foreach {
              _ match {
                case CryptoCurrencyTransactionStatus.Pending =>
                  val pendingItem: CryptoCurrencyTransferItem = item.copy(includedBlock = Some(reOrgBlock))
                  transferMap.put(key, pendingItem)
                  messageHanleResList.append(pendingItem)
                case CryptoCurrencyTransactionStatus.Success =>
                  setReorg(item)
                case CryptoCurrencyTransactionStatus.Confirmed =>
                  setReorg(item)
                case CryptoCurrencyTransactionStatus.Reorging =>
                  setReorg(item)
                case _ =>
              }
            }
          }
        }
    }
  }

  private def refreshLastBlockHeight(tx: CryptoCurrencyTransaction) {
    val txHeight: Long = if (tx.includedBlock.isDefined) tx.includedBlock.get.height.getOrElse(0L) else 0L
    if (manager.getLastBlockHeight < txHeight) {
      manager.setLastBlockHeight(txHeight)
    }
  }

}
