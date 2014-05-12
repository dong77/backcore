package com.coinport.coinex.transfer

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._
import com.mongodb.casbah.Imports._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.support.ChannelSupport
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import Implicits._
import com.coinport.coinex.common.Manager
import scala.Some

class CryptoCurrencyTransferProcessor(val db: MongoDB, accountProcessorPath: ActorPath) extends ExtendedProcessor
    with EventsourcedProcessor with CryptoCurrencyTransferBehavior with ChannelSupport with ActorLogging {
  override def processorId = CRYPTO_TRANSFER_PROCESSOR <<

  val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE
  val manager = new CryptoCurrencyTransferManager()
  setConfirmableHeight(6)

  def receiveRecover = updateState

  def receiveCommand = LoggingReceive {

    case p @ ConfirmablePersistent(MultiCryptoCurrencyTransactionMessage(_, _, _), _, _) =>
      persist(MultiCryptoCurrencyTransactionMessage(_, _, _)) {
        event =>
          updateState(event)
          handleResList()
      }
  }

  def handleResList() {
    getResList foreach {
      item =>
        item.txType.get match {
          case CryptoCurrencyTransactionType.Deposit if item.status.get == CryptoCurrencyTransactionStatus.Success =>
            deliverToAccountManager(DoRequestTransfer(AccountTransfer(item.id.get, 0L /*userId*/ , TransferType.Deposit, item.currency.get, 100 /*item.to.get.amount.get*/ , TransferStatus.Succeeded)))
        }
    }
  }

  private def deliverToAccountManager(event: Any) =
    channelToAccountProcessor forward Deliver(Persistent(event), accountProcessorPath)
}

final class CryptoCurrencyTransferManager extends Manager[TAccountTransferState] {
  var lastTransferId = 1E12.toLong
  var lastBlockHeight = 0L
  val depositSigId2TxPortIdMap = collection.mutable.Map.empty[String, collection.mutable.Map[CryptoCurrencyTransactionPort, Long]]

  def getSnapshot = TAccountTransferState(lastTransferId, getFiltersSnapshot)

  override def loadSnapshot(s: TAccountTransferState) {
    lastTransferId = s.lastTransferId
    loadFiltersSnapshot(s.filters)
  }

  def getNewTransferId = {
    lastTransferId += 1
    lastTransferId
  }

  def setLastTransferId(id: Long) = { lastTransferId = id }

  def getLastBlockHeight = lastBlockHeight

  def setLastBlockHeight(id: Long) = {
    lastBlockHeight = id
  }

  def getDepositTxId(sigId: String, port: CryptoCurrencyTransactionPort): Option[Long] = {
    if (depositSigId2TxPortIdMap.contains(sigId) && depositSigId2TxPortIdMap(sigId).contains(port))
      Some(depositSigId2TxPortIdMap(sigId)(port))
    else
      None
  }

  def saveDepositTxId(sigId: String, port: CryptoCurrencyTransactionPort, id: Long) {
    if (depositSigId2TxPortIdMap.contains(sigId) && depositSigId2TxPortIdMap(sigId).contains(port))
      Some(depositSigId2TxPortIdMap(sigId)(port))
    else
      None
  }
}

