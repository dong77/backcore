package com.coinport.coinex.transfer

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._
import com.coinport.coinex.common.{ ExtendedProcessor, Manager }
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.common.support.ChannelSupport
import com.coinport.coinex.data._
import com.coinport.coinex.data.{ CryptoCurrencyTransactionType => CryptoTxType }
import com.mongodb.casbah.Imports._
import scala.collection.mutable.{ ListBuffer, Map, Set }

import ErrorCode._
import Implicits._
import TransferStatus._

class AccountTransferProcessor(val db: MongoDB, accountProcessorPath: ActorPath, bitwayProcessors: collection.immutable.Map[Currency, ActorRef]) extends ExtendedProcessor
    with EventsourcedProcessor with AccountTransferBehavior with ChannelSupport with ActorLogging {
  override def processorId = ACCOUNT_TRANSFER_PROCESSOR <<

  val manager = new AccountTransferManager()

  private val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE
  private val bitwayChannels =
    Map(bitwayProcessors.keys.toSeq map (
      currency =>
        currency -> createChannelTo(BITWAY_PROCESSOR << currency)
    ): _*)

  private val bitwayBatchSize = 1
  private val userToHotMessages = Set.empty[CryptoCurrencyTransferItem]

  setConfirmableHeight(6)

  override def identifyChannel: PartialFunction[Any, String] = {
    case DoRequestTransfer => "account"
    case MultiCryptoCurrencyTransactionMessage(currency, _, _) => "bitway_" + currency.toString
  }

  def receiveRecover = updateState

  def receiveCommand = LoggingReceive {
    case p @ ConfirmablePersistent(DoRequestTransfer(w), _, _) =>
      persist(DoRequestTransfer(w.copy(id = manager.getTransferId))) {
        event =>
          confirm(p)
          updateState(event)
          if (isCryptoCurrency(w.currency)) {
            w.`type` match {
              case TransferType.Deposit =>
                sender ! RequestTransferFailed(UnsupportTransferType)
              case TransferType.Withdrawal => // accept wait for admin accept
            }
          } else {
            sender ! RequestTransferSucceeded(event.transfer) // wait for admin confirm
          }
      }

    case AdminConfirmTransferFailure(transfer, error) =>
      transferHandler.get(transfer.id) match {
        case Some(transfer) if transfer.status == Pending =>
          val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Failed, reason = Some(error))
          persist(AdminConfirmTransferFailure(updated, error)) { event =>
            sender ! AdminCommandResult(Ok)
            deliverToAccountManager(event)
            updateState(event)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmTransferSuccess(transfer) =>
      transferHandler.get(transfer.id) match {
        case Some(tranfer) if transfer.status == Pending =>
          if (isCryptoCurrency(transfer.currency)) {
            transfer.`type` match {
              case TransferType.Deposit => sender ! RequestTransferFailed(UnsupportTransferType)
              case TransferType.Withdrawal =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Accepted)
                persist(AdminConfirmTransferSuccess(updated)) {
                  event =>
                    updateState(event)
                    handleResList() // need send message to bitway for withdraw
                    sender ! AdminCommandResult(Ok)
                }
            }
          } else {
            val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Succeeded)
            persist(AdminConfirmTransferSuccess(updated)) {
              event =>
                deliverToAccountManager(event)
                updateState(event)
                sender ! AdminCommandResult(Ok)
            }
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case p @ ConfirmablePersistent(msg: MultiCryptoCurrencyTransactionMessage, _, _) =>
      persist(msg) {
        event =>
          confirm(p)
          updateState(event)
          handleResList()
      }
  }

  private def handleResList() {
    getMessagesBox foreach {
      item =>
        //        println(s"MessagesBox got item => ${item.toString}")
        item.txType.get match {
          case CryptoTxType.Deposit if item.status.get == Succeeded =>
            deliverToAccountManager(CryptoTransferSucceeded(transferHandler.get(item.accountTransferId.get).get))
          case CryptoTxType.UserToHot if item.status.get == Confirming =>
            batchSendBitwayMessage(item)
          case CryptoTxType.Withdrawal =>
            item.status.get match {
              case Confirming =>
                val info = CryptoCurrencyTransferInfo(item.id.get, Some(item.to.get.address), item.to.get.internalAmount, item.to.get.amount, None)
                deliverToBitwayProcessor(item.currency.get, TransferCryptoCurrency(item.currency.get, List(info), CryptoTxType.Withdrawal))
              case Succeeded =>
                deliverToAccountManager(CryptoTransferSucceeded(transferHandler.get(item.accountTransferId.get).get))
              case Failed =>
                deliverToAccountManager(CryptoTransferFailed(transferHandler.get(item.accountTransferId.get).get, ErrorCode.BitwayProcessFail))
              case _ =>
            }
          case _ => // TODO handle ColdToHot and HotToCold message
        }
    }
    getMongoWriteList foreach {
      item =>
        //        println(s"MessagesBox got item => ${item.toString}")
        transferItemHandler.put(item)
    }
  }

  private def batchSendBitwayMessage(item: CryptoCurrencyTransferItem) {
    userToHotMessages.add(item)
    if (userToHotMessages.size >= bitwayBatchSize) {
      val transferInfos = ListBuffer.empty[CryptoCurrencyTransferInfo]
      userToHotMessages foreach {
        item =>
          transferInfos.append(CryptoCurrencyTransferInfo(item.id.get, None, item.from.get.internalAmount, item.from.get.amount, Some(item.from.get.address)))
      }
      deliverToBitwayProcessor(item.currency.get, TransferCryptoCurrency(item.currency.get, transferInfos.toList, CryptoTxType.UserToHot))
      userToHotMessages.clear()
    }
  }

  private def deliverToAccountManager(event: Any) =
    channelToAccountProcessor forward Deliver(Persistent(event), accountProcessorPath)

  private def deliverToBitwayProcessor(currency: Currency, event: Any) =
    bitwayChannels(currency) forward Deliver(Persistent(event), bitwayProcessors(currency).path)
}

final class AccountTransferManager extends Manager[TAccountTransferState] {
  private var lastTransferId = 1E12.toLong
  private var lastTransferItemId = 1E12.toLong
  private var lastBlockHeight = 0L
  private val depositSigId2TxPortIdMap = Map.empty[String, Map[CryptoCurrencyTransactionPort, Long]]

  def getSnapshot = TAccountTransferState(lastTransferId, lastTransferItemId, lastBlockHeight, depositSigId2TxPortIdMap, getFiltersSnapshot)

  override def loadSnapshot(s: TAccountTransferState) {
    lastTransferId = s.lastTransferId
    lastTransferItemId = s.lastTransferItemId
    lastBlockHeight = s.lastBlockHeight
    depositSigId2TxPortIdMap.putAll(s.depositSigId2TxPortIdMap)
    loadFiltersSnapshot(s.filters)
  }

  def getTransferId = lastTransferId + 1

  def setLastTransferId(id: Long) = { lastTransferId = id }

  def getLastTransferItemId = lastTransferItemId

  def getNewTransferItemId = {
    lastTransferItemId += 1
    lastTransferItemId
  }

  def getLastBlockHeight = lastBlockHeight

  def setLastBlockHeight(id: Long) = { lastBlockHeight = id }

  def getDepositTxId(sigId: String, port: CryptoCurrencyTransactionPort): Option[Long] = {
    if (depositSigId2TxPortIdMap.contains(sigId) && depositSigId2TxPortIdMap(sigId).contains(port))
      Some(depositSigId2TxPortIdMap(sigId)(port))
    else
      None
  }

  def saveDepositTxId(sigId: String, port: CryptoCurrencyTransactionPort, id: Long) {
    depositSigId2TxPortIdMap.getOrElse(sigId, Map.empty[CryptoCurrencyTransactionPort, Long]).put(port, id)
  }

  def removeDepositTxid(sigId: String, port: CryptoCurrencyTransactionPort) {
    if (depositSigId2TxPortIdMap.contains(sigId)) {
      depositSigId2TxPortIdMap(sigId).remove(port)
      if (depositSigId2TxPortIdMap(sigId).isEmpty) {
        depositSigId2TxPortIdMap.remove(sigId)
      }
    }
  }
}
