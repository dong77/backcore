package com.coinport.coinex.transfer

import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import akka.event.LoggingReceive
import com.coinport.coinex.common.PersistentId._
import Implicits._
import com.coinport.coinex.common.ExtendedView

class CryptoCurrencyTransferView(val db: MongoDB) extends CryptoCurrencyTransferBehavior with ExtendedView {
  override val processorId = CRYPTO_TRANSFER_PROCESSOR <<
  val manager = new CryptoCurrencyTransferManager()

  def receive = LoggingReceive {
    case p @ MultiCryptoCurrencyTransactionMessage(_, _, _) =>
      updateState(p)
      getResList foreach { item => cryptoCurrencyTransferHandler.put(item) }
  }

  val cryptoCurrencyTransferHandler = new SimpleJsonMongoCollection[CryptoCurrencyTransferItem, CryptoCurrencyTransferItem.Immutable]() {
    lazy val coll = db("cryptotransfers")

    def extractId(item: CryptoCurrencyTransferItem) = item.id.get

    def queryTransferItems(q: QueryCryptoCurrencyTransfer): Seq[CryptoCurrencyTransferItem] = {
      var query = MongoDBObject()
      if (q.id.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.IdField.name -> q.id.get)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.CurrencyField.name -> q.currency.get)
      if (q.sigId.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.SigIdField.name -> q.sigId.get)
      if (q.txType.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.TxTypeField.name -> q.txType.get)
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.StatusField.name -> q.status.get)
      find(query, 0, 1)
    }

    def queryNeedConfirmCCTxTransfer(q: QueryNeedConfirmCryptoCurrencyTransfer): Seq[CryptoCurrencyTransferItem] = {
      var query = MongoDBObject()
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.CurrencyField.name -> q.currency.get)
      if (q.statuses.isDefined) {
        val statusStrings = for (status <- q.statuses) yield (status.toString())
        query ++= (DATA + "." + CryptoCurrencyTransferItem.StatusField.name $in statusStrings.toSeq)
      }
      query ++= (DATA + "." + CryptoCurrencyTransferItem.IncludedBlockField.name $exists true)
      find(query, 0, 100)
    }
  }
}
