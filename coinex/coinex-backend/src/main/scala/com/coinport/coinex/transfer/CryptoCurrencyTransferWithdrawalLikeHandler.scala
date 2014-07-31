package com.coinport.coinex.transfer

import com.coinport.coinex.data._
import com.coinport.coinex.data.TransferStatus.Confirming

class CryptoCurrencyTransferWithdrawalLikeHandler extends CryptoCurrencyTransferHandler {
  def this(item: CryptoCurrencyTransferItem)(implicit env: TransferEnv) {
    this()
    this.item = item
    setEnv(env, None)
  }

  override def prepareItem(tx: CryptoCurrencyTransaction) {
    super.prepareItem(tx)
    if (item.currency == Currency.Nxt && item.txType == Some(TransferType.Withdrawal)) {
      if (tx.outputs.isDefined && tx.outputs.get.size == 1) {
        item = item.copy(to = Some(item.to.get.copy(address = tx.outputs.get.head.address, nxtRsAddress = tx.outputs.get.head.nxtRsAddress)))
      } else {
        logger.error(s"nxt withdrawal tx.outputs is invalid : ${tx.toString}")
      }
    }
  }

  override def prepareTransfer(transfer: AccountTransfer): AccountTransfer = {
    if (transfer.currency == Currency.Nxt && item.txType == Some(TransferType.Withdrawal)) {
      if (item.to.isDefined) {
        transfer.copy(address = Some(item.to.get.address), nxtRsAddress = item.to.get.nxtRsAddress)
      } else {
        logger.error(s"nxt withdrawal not define to port: ${item.toString}")
        transfer
      }
    } else {
      transfer
    }
  }

  def this(t: AccountTransfer, from: Option[CryptoCurrencyTransactionPort], to: Option[CryptoCurrencyTransactionPort], timestamp: Option[Long])(implicit env: TransferEnv) {
    this()
    setEnv(env, timestamp)
    // id, currency, sigId, txid, userId, from, to(user's internal address), includedBlock, txType, status, userToHotMapedDepositId, accountTransferId, created, updated
    item = CryptoCurrencyTransferItem(env.manager.getNewTransferItemId, t.currency, None, None, Some(t.userId), from, to, None, Some(t.`type`), Some(Confirming), None, Some(t.id), timestamp)
    saveItemToMongo()
  }
}
