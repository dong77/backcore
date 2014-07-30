package com.coinport.coinex.transfer

import com.coinport.coinex.data._
import com.coinport.coinex.data.TransferStatus._

class CryptoCurrencyTransferDepositLikeHandler(currency: Currency, outputPort: CryptoCurrencyTransactionPort, tx: CryptoCurrencyTransaction, timestamp: Option[Long])(implicit env: TransferEnv) extends CryptoCurrencyTransferHandler {
  setEnv(env, timestamp)
  // only coldToHot will use this constructor, Deposit will use override version
  if (currency != null && outputPort != null && tx != null) {
    val transferId = manager.getTransferId
    manager.setLastTransferId(transferId)
    transferHandler.put(AccountTransfer(transferId, outputPort.userId.get, tx.txType.get, currency, outputPort.internalAmount.get, Confirming, getTimestamp(), getTimestamp(), address = Some(outputPort.address), txid = tx.txid, nxtRsAddress = outputPort.nxtRsAddress))
    val newTransferItemId = manager.getNewTransferItemId
    // id, currency, sigId, txid, userId, from, to(user's internal address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
    item = CryptoCurrencyTransferItem(newTransferItemId, currency, tx.sigId, tx.txid, outputPort.userId, None, Some(outputPort), tx.includedBlock, tx.txType, Some(Confirming), None, Some(transferId), getTimestamp(), None, tx.minerFee)
    saveItemToMongo()
  }

  def this(item: CryptoCurrencyTransferItem)(implicit env: TransferEnv) {
    this(null, null, null, None)
    this.item = item
  }
}
