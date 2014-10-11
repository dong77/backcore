package com.coinport.coinex.transfer

import com.coinport.coinex.data._
import com.coinport.coinex.data.TransferStatus._
import com.coinport.coinex.common.Constants._

import scala.collection.mutable.Map

object CryptoCurrencyTransferUsersToInnerHandler extends CryptoCurrencyTransferBase {
  val sigId2HandlerMap = Map.empty[String, CryptoCurrencyTransferHandler]

  override def handleSucceeded(itemId: Long) {
    if (id2HandlerMap.contains(itemId)) {
      val item = id2HandlerMap(itemId).item
      removeItemHandlerFromMap(item.sigId.get)
    }
    super.handleSucceeded(itemId)
  }

  override def handleFailed(handler: CryptoCurrencyTransferHandler, error: Option[ErrorCode] = None) {
    handler.onFail()
    id2HandlerMap.remove(handler.item.id)
    removeItemHandlerFromMap(handler.item.sigId.get)
  }

  override def loadSnapshotItems(snapshotItems: Option[collection.Map[Long, CryptoCurrencyTransferItem]]) {
    super.loadSnapshotItems(snapshotItems)
    sigId2HandlerMap.clear()
    if (snapshotItems.isDefined) {
      snapshotItems.get.values map {
        item =>
          sigId2HandlerMap.put(item.sigId.get, id2HandlerMap(item.id))
      }
    }
  }

  override def innerHandleTx(currency: Currency, tx: CryptoCurrencyTransaction, timestamp: Option[Long]) {
    tx.outputs match {
      case Some(outputList) if outputList.nonEmpty && tx.inputs.isDefined && tx.inputs.get.nonEmpty =>
        val validOutputs = outputList filter (out => out.userId == Some(COLD_UID) || out.userId == Some(HOT_UID))
        val validInputs = tx.inputs.get filter (in => in.userId.isDefined && in.userId != Some(COLD_UID) && in.userId != Some(HOT_UID))
        if (validOutputs.nonEmpty) {
          tx.status match {
            case Failed =>
              getItemHandlerFromMap(tx.sigId.get) match {
                case Some(handler) =>
                  handleFailed(handler.setTimeStamp(timestamp))
                case None =>
              }
            case _ =>
              getItemHandlerFromMap(tx.sigId.get) match {
                case Some(handler) =>
                  handler.setTimeStamp(timestamp).onNormal(tx)
                case _ =>
                  val hd = new CryptoCurrencyTransferUsersToInnerHandler(currency, validInputs.toList, validOutputs.toList, tx, timestamp)
                  saveItemHandlerToMap(tx.sigId.get, hd)
                  id2HandlerMap.put(hd.item.id, hd)
              }
          }
          updateSigId2MinerFee(tx)
        } else {
          logger.warning(s"""${"~" * 50} ${currency.toString} innerHandleTx() ${tx.txType.get.toString} tx have not valid outputs : ${tx.toString}""")
        }
      case _ =>
        logger.warning(s"""${"~" * 50} ${currency.toString} innerHandleTx() ${tx.txType.get.toString} tx have not valid inputs or outputs : ${tx.toString}""")
    }
  }

  override def newHandlerFromItem(item: CryptoCurrencyTransferItem): CryptoCurrencyTransferHandler = {
    new CryptoCurrencyTransferUsersToInnerHandler(item)
  }

  private def getItemHandlerFromMap(sigId: String): Option[CryptoCurrencyTransferHandler] = {
    if (sigId2HandlerMap.contains(sigId))
      Some(sigId2HandlerMap(sigId))
    else
      None
  }

  protected def removeItemHandlerFromMap(sigId: String) {
    if (sigId2HandlerMap.contains(sigId)) {
      sigId2HandlerMap.remove(sigId)
    }
  }

  private def saveItemHandlerToMap(sigId: String, handler: CryptoCurrencyTransferHandler) {
    sigId2HandlerMap.put(sigId, handler)
  }
}

class CryptoCurrencyTransferUsersToInnerHandler(currency: Currency, inputs: List[CryptoCurrencyTransactionPort], outputs: List[CryptoCurrencyTransactionPort], tx: CryptoCurrencyTransaction, timestamp: Option[Long])(implicit env: TransferEnv) extends CryptoCurrencyTransferHandler {
  setEnv(env, timestamp)
  if (currency != null && inputs != null && outputs != null && tx != null) {
    val transferId = manager.getTransferId
    manager.setLastTransferId(transferId)
    val internalAmount = inputs.map(_.internalAmount.get).reduce(_ + _)
    transferHandler.put(AccountTransfer(transferId, COINPORT_UID, tx.txType.get, currency, internalAmount, Confirming, getTimestamp(), getTimestamp(), txid = tx.txid))
    val newTransferItemId = manager.getNewTransferItemId
    // id, currency, sigId, txid, userId, from, to(user's internal address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
    item = CryptoCurrencyTransferItem(newTransferItemId, currency, tx.sigId, tx.txid, Some(COINPORT_UID), None, None, tx.includedBlock, tx.txType, Some(Confirming), None, Some(transferId), getTimestamp(), None, tx.minerFee, froms = Some(inputs), tos = Some(outputs))
    saveItemToMongo()
  }

  def this(item: CryptoCurrencyTransferItem)(implicit env: TransferEnv) {
    this(null, null, null, null, None)
    this.item = item
  }

}
