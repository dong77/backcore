package com.coinport.coinex.transfer

import akka.actor._
import akka.event.LoggingReceive
import akka.event.LoggingAdapter
import akka.persistence._
import com.coinport.coinex.common.{ ExtendedProcessor, Manager }
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.common.support.ChannelSupport
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import scala.collection.mutable.{ ListBuffer, Map, Set }

import ErrorCode._
import Implicits._
import TransferStatus._
import TransferType._

class AccountTransferProcessor(val db: MongoDB, accountProcessorPath: ActorPath, bitwayProcessors: collection.immutable.Map[Currency, ActorRef]) extends ExtendedProcessor
    with EventsourcedProcessor with ChannelSupport with AccountTransferBehavior with ActorLogging {
  override val processorId = ACCOUNT_TRANSFER_PROCESSOR <<

  lazy implicit val logger: LoggingAdapter = log

  val manager = new AccountTransferManager()
  val transferDebugConfig = context.system.settings.config.getBoolean("akka.exchange.account-transfer-debug")
  private val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE
  private val bitwayChannels = bitwayProcessors.map(kv => kv._1 -> createChannelTo(BITWAY_PROCESSOR << kv._1))

  setConfirmableHeight(1)
  setTransferDebug(transferDebugConfig)

  override def identifyChannel: PartialFunction[Any, String] = {
    case r: DoRequestTransfer => "account"
    case MultiCryptoCurrencyTransactionMessage(currency, _, _) => "bitway_" + currency.toString.toLowerCase()
  }

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case p @ ConfirmablePersistent(DoRequestTransfer(w), _, _) =>
      persist(DoRequestTransfer(w.copy(id = manager.getTransferId))) {
        event =>
          confirm(p)
          updateState(event)
          if (isCryptoCurrency(w.currency) && !transferDebugConfig) {
            w.`type` match {
              case TransferType.Deposit =>
                sender ! RequestTransferFailed(UnsupportTransferType)
              case TransferType.UserToHot =>
                sender ! RequestTransferFailed(UnsupportTransferType)
              case TransferType.Withdrawal => // accept wait for admin accept
              case TransferType.ColdToHot => // accept, wait for admin confirm
              case TransferType.HotToCold => // accept, save request to map
              case TransferType.Unknown => // accept wait for admin accept
                sender ! RequestTransferFailed(UnsupportTransferType)
            }
          } else {
            sender ! RequestTransferSucceeded(event.transfer) // wait for admin confirm
          }
      }

    case AdminConfirmTransferFailure(transfer, error) =>
      transferHandler.get(transfer.id) match {
        case Some(transfer) if transfer.status == Pending =>
          val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Failed, reason = Some(error))
          persist(AdminConfirmTransferFailure(updated, error)) {
            event =>
              sender ! AdminCommandResult(Ok)
              if (transfer.`type` != ColdToHot) {
                deliverToAccountManager(event)
              }
              updateState(event)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmTransferSuccess(transfer) =>
      transferHandler.get(transfer.id) match {
        case Some(tranfer) if transfer.status == Pending =>
          if (isCryptoCurrency(transfer.currency) && !transferDebugConfig) {
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
              case TransferType.ColdToHot =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Succeeded)
                persist(AdminConfirmTransferSuccess(updated)) {
                  event =>
                    updateState(event)
                    sender ! AdminCommandResult(Ok)
                }
              case _ =>
                sender ! RequestTransferFailed(UnsupportTransferType)
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
        println(s" ---------------------------- MessagesBox got item => ${item.toString}")
        item.txType.get match {
          case Deposit if item.status.get == Succeeded =>
            deliverToAccountManager(CryptoTransferSucceeded(transferHandler.get(item.accountTransferId.get).get))
          case UserToHot =>
            handleMessage(CryptoCurrencyTransferInfo(item.id.get, None, item.from.get.internalAmount, item.from.get.amount, Some(item.from.get.address)), item)
          case Withdrawal =>
            handleMessage(CryptoCurrencyTransferInfo(item.id.get, Some(item.to.get.address), item.to.get.internalAmount, item.to.get.amount, None), item)
          case ColdToHot =>
            handleMessage(CryptoCurrencyTransferInfo(item.id.get, None, item.to.get.internalAmount, None, None), item)
          case HotToCold =>
            handleMessage(CryptoCurrencyTransferInfo(item.id.get, None, item.to.get.internalAmount, None, None), item)
          case _ =>
        }
    }
    getMongoWriteList foreach {
      item =>
        println(s" =========================== getMongoWriteList got item => ${item.toString}")
        transferItemHandler.put(item)
    }
  }

  def handleMessage(info: CryptoCurrencyTransferInfo, item: CryptoCurrencyTransferItem) {
    item.status.get match {
      case Confirming =>
        deliverToBitwayProcessor(item.currency.get, TransferCryptoCurrency(item.currency.get, List(info), item.txType.get))
      case Succeeded =>
        deliverToAccountManager(CryptoTransferSucceeded(transferHandler.get(item.accountTransferId.get).get))
      case Failed if item.txType.get != UserToHot && item.txType != ColdToHot => //UserToHot fail will do nothing
        deliverToAccountManager(CryptoTransferFailed(transferHandler.get(item.accountTransferId.get).get, ErrorCode.BitwayProcessFail))
      case _ =>
    }
  }

  private def deliverToAccountManager(event: Any) = {
    println(s">>>>>>>>>>>>>>>>>>>>> deliverToAccountManager => event = ${event.toString}")
    channelToAccountProcessor forward Deliver(Persistent(event), accountProcessorPath)
  }

  private def deliverToBitwayProcessor(currency: Currency, event: Any) = {
    println(s">>>>>>>>>>>>>>>>>>>>> deliverToBitwayProcessor => currency = ${currency.toString}, event = ${event.toString}, path = ${bitwayProcessors(currency).path.toString}")
    bitwayChannels(currency) forward Deliver(Persistent(event), bitwayProcessors(currency).path)
  }
}

class AccountTransferManager() extends Manager[TAccountTransferState] {
  private var lastTransferId = 1E12.toLong
  private var lastTransferItemId = 6E12.toLong
  private var lastBlockHeight = 0L
  private val depositSigId2TxPortIdMap = Map.empty[String, Map[CryptoCurrencyTransactionPort, Long]]
  private val coldToHotSigId2IdMap = Map.empty[String, Map[CryptoCurrencyTransactionPort, Long]]
  private val transferMapInnner = Map.empty[Long, CryptoCurrencyTransferItem]
  private val succeededMapInnner = Map.empty[Long, CryptoCurrencyTransferItem]

  def getSnapshot = TAccountTransferState(
    lastTransferId,
    lastTransferItemId,
    lastBlockHeight,
    depositSigId2TxPortIdMap.clone,
    coldToHotSigId2IdMap.clone(),
    transferMapInnner.clone(),
    succeededMapInnner.clone(),
    getFiltersSnapshot)

  def loadSnapshot(s: TAccountTransferState) = {
    lastTransferId = s.lastTransferId
    lastTransferItemId = s.lastTransferItemId
    lastBlockHeight = s.lastBlockHeight
    depositSigId2TxPortIdMap ++= s.depositSigId2TxPortIdMap map { kv => (kv._1 -> (Map.empty ++ kv._2)) }
    coldToHotSigId2IdMap ++= s.coldToHotSigId2IdMap map { kv => (kv._1 -> (Map.empty ++ kv._2)) }
    transferMapInnner ++= s.transferMap
    succeededMapInnner ++= s.succeededMap
    loadFiltersSnapshot(s.filters)
  }

  def getTransferId = lastTransferId + 1

  def setLastTransferId(id: Long) = { lastTransferId = id }

  def getLastTransferItemId = lastTransferItemId

  def getNewTransferItemId = {
    lastTransferItemId += 1
    lastTransferItemId
  }

  def transferMap = transferMapInnner

  def succeededMap = succeededMapInnner

  def getLastBlockHeight = lastBlockHeight

  def setLastBlockHeight(height: Long) = { lastBlockHeight = height }

  def getDepositTxId(sigId: String, port: CryptoCurrencyTransactionPort): Option[Long] = {
    getIdFromMap(depositSigId2TxPortIdMap, sigId, port)
  }

  def getColdTxId(sigId: String, port: CryptoCurrencyTransactionPort): Option[Long] = {
    getIdFromMap(coldToHotSigId2IdMap, sigId, port)
  }

  def getIdFromMap(operateMap: Map[String, Map[CryptoCurrencyTransactionPort, Long]], sigId: String, port: CryptoCurrencyTransactionPort): Option[Long] = {
    if (operateMap.contains(sigId) && operateMap(sigId).contains(port))
      Some(operateMap(sigId)(port))
    else
      None
  }

  def saveDepositTxId(sigId: String, port: CryptoCurrencyTransactionPort, id: Long) {
    saveIdToMap(depositSigId2TxPortIdMap, sigId, port, id)
  }

  def saveColdTxId(sigId: String, port: CryptoCurrencyTransactionPort, id: Long) {
    saveIdToMap(coldToHotSigId2IdMap, sigId, port, id)
  }

  def saveIdToMap(operateMap: Map[String, Map[CryptoCurrencyTransactionPort, Long]], sigId: String, port: CryptoCurrencyTransactionPort, id: Long) {
    if (!operateMap.contains(sigId)) {
      operateMap.put(sigId, Map.empty[CryptoCurrencyTransactionPort, Long])
    }
    operateMap(sigId).put(port, id)
  }

  def removeDepositTxid(sigId: String, port: CryptoCurrencyTransactionPort) {
    removeIdFromMap(depositSigId2TxPortIdMap, sigId, port)
  }

  def removeColdTxId(sigId: String, port: CryptoCurrencyTransactionPort) {
    removeIdFromMap(coldToHotSigId2IdMap, sigId, port)
  }

  def removeIdFromMap(operateMap: Map[String, Map[CryptoCurrencyTransactionPort, Long]], sigId: String, port: CryptoCurrencyTransactionPort) {
    if (operateMap.contains(sigId)) {
      operateMap(sigId).remove(port)
      if (operateMap(sigId).isEmpty) {
        operateMap.remove(sigId)
      }
    }
  }
}
