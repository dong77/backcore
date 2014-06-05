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
import scala.collection.mutable.{ Map, ListBuffer }

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
  val transferConfirmableHeight = context.system.settings.config.getInt("akka.exchange.transfer-confirmable-height")
  private val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE
  private val bitwayChannels = bitwayProcessors.map(kv => kv._1 -> createChannelTo(BITWAY_PROCESSOR << kv._1))
  private val sigId2ItemListMap = Map.empty[String, ListBuffer[CryptoCurrencyTransferItem]]

  setConfirmableHeight(transferConfirmableHeight)
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
            handleResList()
          } else {
            sender ! RequestTransferSucceeded(event.transfer) // wait for admin confirm
          }
      }

    case AdminConfirmTransferFailure(t, error) =>
      transferHandler.get(t.id) match {
        case Some(transfer) if transfer.status == Pending =>
          val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Failed, reason = Some(error))
          persist(AdminConfirmTransferFailure(updated, error)) {
            event =>
              sender ! AdminCommandResult(Ok)
              deliverToAccountManager(event)
              updateState(event)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(TransferNotExist)
      }

    case DoCancelTransfer(t) =>
      transferHandler.get(t.id) match {
        case Some(transfer) if transfer.status == Pending =>
          if (t.userId == transfer.userId) {
            val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Failed, reason = Some(ErrorCode.UserCanceled))
            if (transfer.`type` == ColdToHot || transfer.`type` == Withdrawal) {
              persist(DoCancelTransfer(updated)) {
                event =>
                  sender ! AdminCommandResult(Ok)
                  deliverToAccountManager(event)
                  updateState(event)
              }
            } else {
              sender ! AdminCommandResult(UnsupportTransferType)
            }
          } else {
            sender ! AdminCommandResult(UserAuthenFail)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(TransferNotExist)
      }

    case AdminConfirmTransferSuccess(t) =>
      transferHandler.get(t.id) match {
        case Some(transfer) if transfer.status == Pending =>
          if (isCryptoCurrency(transfer.currency) && !transferDebugConfig) {
            transfer.`type` match {
              case TransferType.Deposit => sender ! RequestTransferFailed(UnsupportTransferType)
              case TransferType.Withdrawal if transfer.address.isDefined =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Accepted)
                persist(AdminConfirmTransferSuccess(updated)) {
                  event =>
                    updateState(event)
                    handleResList()
                    sender ! AdminCommandResult(Ok)
                }
              case TransferType.ColdToHot =>
                val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = Accepted)
                persist(AdminConfirmTransferSuccess(updated)) {
                  event =>
                    updateState(event)
                    handleResList()
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
        case None => sender ! AdminCommandResult(TransferNotExist)
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
        log.info(s" ---------------------------- MessagesBox got item => ${item.toString}")
        item.txType.get match {
          case Deposit if item.status.get == Succeeded =>
            deliverToAccountManager(CryptoTransferSucceeded(item.txType.get, List(transferHandler.get(item.accountTransferId.get).get), None))
          case UserToHot =>
            handleMessage(CryptoCurrencyTransferInfo(item.id, None, item.from.get.internalAmount, item.from.get.amount, Some(item.from.get.address)), item)
          case Withdrawal =>
            handleMessage(CryptoCurrencyTransferInfo(item.id, Some(item.to.get.address), item.to.get.internalAmount, item.to.get.amount, None), item)
          case ColdToHot =>
            handleMessage(CryptoCurrencyTransferInfo(item.id, None, item.to.get.internalAmount, None, None), item)
          case HotToCold =>
            handleMessage(CryptoCurrencyTransferInfo(item.id, None, item.to.get.internalAmount, None, None), item)
          case _ =>
        }
    }
    batchSendMinerFeeTransfer
    getMongoWriteList foreach {
      item =>
        log.info(s" =========================== getMongoWriteList got item => ${item.toString}")
        transferItemHandler.put(item)
    }
  }

  def handleMessage(info: CryptoCurrencyTransferInfo, item: CryptoCurrencyTransferItem) {
    item.status.get match {
      case Confirming =>
        deliverToBitwayProcessor(item.currency, TransferCryptoCurrency(item.currency, List(info), item.txType.get))
      case Succeeded => // batch Set miner fee before sent message to accountProcessor
        batchMinerFeeTransfer(item)
      case Failed if item.txType.get != UserToHot && item.txType != ColdToHot => //UserToHot fail will do nothing
        deliverToAccountManager(CryptoTransferFailed(transferHandler.get(item.accountTransferId.get).get, ErrorCode.BitwayProcessFail))
      case _ =>
    }
  }

  def batchMinerFeeTransfer(item: CryptoCurrencyTransferItem) {
    item.sigId foreach {
      sigId =>
        sigId2ItemListMap.contains(sigId) match {
          case true =>
            sigId2ItemListMap(sigId).append(item)
          case false =>
            val buffer = ListBuffer.empty[CryptoCurrencyTransferItem]
            buffer.append(item)
            sigId2ItemListMap.put(sigId, buffer)
        }
    }
  }

  def batchSendMinerFeeTransfer() {
    sigId2ItemListMap.keys foreach {
      sigId =>
        val transfers = sigId2ItemListMap(sigId).map(item => transferHandler.get(item.accountTransferId.get).get)
        deliverToAccountManager(CryptoTransferSucceeded(transfers(0).`type`, transfers.toList, manager.sigId2MinerFeeMap.remove(sigId)))
    }
    sigId2ItemListMap.clear()
  }

  private def deliverToAccountManager(event: Any) = {
    log.info(s">>>>>>>>>>>>>>>>>>>>> deliverToAccountManager => event = ${event.toString}")
    channelToAccountProcessor forward Deliver(Persistent(event), accountProcessorPath)
  }

  private def deliverToBitwayProcessor(currency: Currency, event: Any) = {
    log.info(s">>>>>>>>>>>>>>>>>>>>> deliverToBitwayProcessor => currency = ${currency.toString}, event = ${event.toString}, path = ${bitwayProcessors(currency).path.toString}")
    bitwayChannels(currency) forward Deliver(Persistent(event), bitwayProcessors(currency).path)
  }
}

class AccountTransferManager() extends Manager[TAccountTransferState] {
  private var lastTransferId = 1E12.toLong
  private var lastTransferItemId = 6E12.toLong
  private var lastBlockHeight = Map.empty[Currency, Long]
  private val depositSigId2TxPortIdMapInner = Map.empty[String, Map[CryptoCurrencyTransactionPort, Long]]
  private val coldToHotSigId2TxPortIdMapInner = Map.empty[String, Map[CryptoCurrencyTransactionPort, Long]]
  private val transferMapInnner = Map.empty[Long, CryptoCurrencyTransferItem]
  private val succeededMapInnner = Map.empty[Long, CryptoCurrencyTransferItem]
  private val sigId2MinerFeeMapInnner = Map.empty[String, Long]

  def getSnapshot = TAccountTransferState(
    lastTransferId,
    lastTransferItemId,
    lastBlockHeight,
    depositSigId2TxPortIdMapInner.clone,
    coldToHotSigId2TxPortIdMapInner.clone(),
    transferMapInnner.clone(),
    succeededMapInnner.clone(),
    sigId2MinerFeeMapInnner.clone(),
    getFiltersSnapshot)

  def loadSnapshot(s: TAccountTransferState) = {
    lastTransferId = s.lastTransferId
    lastTransferItemId = s.lastTransferItemId
    lastBlockHeight ++= s.lastBlockHeight
    depositSigId2TxPortIdMapInner ++= s.depositSigId2TxPortIdMapInner map { kv => (kv._1 -> (Map.empty ++ kv._2)) }
    coldToHotSigId2TxPortIdMapInner ++= s.coldToHotSigId2TxPortIdMapInner map { kv => (kv._1 -> (Map.empty ++ kv._2)) }
    transferMapInnner ++= s.transferMap
    succeededMapInnner ++= s.succeededMap
    sigId2MinerFeeMapInnner ++= s.sigId2MinerFeeMapInnner
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

  def sigId2MinerFeeMap = sigId2MinerFeeMapInnner

  def getLastBlockHeight(currency: Currency): Long = lastBlockHeight.getOrElse(currency, 0L)

  def setLastBlockHeight(currency: Currency, height: Long) = { lastBlockHeight.put(currency, height) }

  def depositSigId2TxPortIdMap = depositSigId2TxPortIdMapInner

  def coldToHotSigId2TxPortIdMap = coldToHotSigId2TxPortIdMapInner

  def getItemIdFromMap(operateMap: Map[String, Map[CryptoCurrencyTransactionPort, Long]], sigId: String, port: CryptoCurrencyTransactionPort): Option[Long] = {
    if (operateMap.contains(sigId) && operateMap(sigId).contains(port))
      Some(operateMap(sigId)(port))
    else
      None
  }

  def saveItemIdToMap(operateMap: Map[String, Map[CryptoCurrencyTransactionPort, Long]], sigId: String, port: CryptoCurrencyTransactionPort, id: Long) {
    if (!operateMap.contains(sigId)) {
      operateMap.put(sigId, Map.empty[CryptoCurrencyTransactionPort, Long])
    }
    operateMap(sigId).put(port, id)
  }

  def removeItemIdFromMap(operateMap: Map[String, Map[CryptoCurrencyTransactionPort, Long]], sigId: String, port: CryptoCurrencyTransactionPort) {
    if (operateMap.contains(sigId)) {
      operateMap(sigId).remove(port)
      if (operateMap(sigId).isEmpty) {
        operateMap.remove(sigId)
      }
    }
  }
}
