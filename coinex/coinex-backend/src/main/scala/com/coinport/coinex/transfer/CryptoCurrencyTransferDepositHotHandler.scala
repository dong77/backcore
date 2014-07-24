package com.coinport.coinex.transfer

import com.coinport.coinex.data._

object CryptoCurrencyTransferDepositHotHandler extends CryptoCurrencyTransferDepositLikeBase {

  override def item2CryptoCurrencyTransferInfo(item: CryptoCurrencyTransferItem): Option[CryptoCurrencyTransferInfo] = {
    Some(CryptoCurrencyTransferInfo(item.id, None, item.to.get.internalAmount, item.to.get.amount, None))
  }

  override def newHandlerFromItem(item: CryptoCurrencyTransferItem): CryptoCurrencyTransferHandler = {
    new CryptoCurrencyTransferDepositHotHandler(item)
  }
}

class CryptoCurrencyTransferDepositHotHandler(currency: Currency, outputPort: CryptoCurrencyTransactionPort, tx: CryptoCurrencyTransaction, timestamp: Option[Long])(implicit env: TransferEnv)
    extends CryptoCurrencyTransferDepositLikeHandler(currency, outputPort, tx, timestamp) {

  def this(item: CryptoCurrencyTransferItem)(implicit env: TransferEnv) {
    this(null, null, null, None)
    this.item = item
  }

  override def onNormal(tx: CryptoCurrencyTransaction) {
    // ignore minerFee for Deposit, as it is payed by user
    super.onNormal(tx.copy(minerFee = None))
  }

}

