/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import akka.actor.{ Cancellable, Actor, ActorLogging, ActorRef }
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import akka.persistence.Deliver
import akka.persistence.Persistent
import akka.persistence.ConfirmablePersistent
import akka.persistence.EventsourcedProcessor

import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import scala.collection.mutable.Set
import scala.concurrent.duration._

import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.data.FetchAddresses
import com.coinport.coinex.data.TransferBetweenHotCold
import com.coinport.coinex.data.TransferType._
import com.coinport.coinex.serializers._
import Implicits._
import scala.Some

class BitwayProcessor(transferProcessor: ActorRef, supportedCurrency: Currency, config: BitwayConfig)
    extends ExtendedProcessor with EventsourcedProcessor with BitwayManagerBehavior with ActorLogging {

  import BlockContinuityEnum._

  var sendGetMissedBlockTime = 0L
  val RESEND_GET_MISSED_BLOCK_TIMEOUT = 30 * 1000L
  val HOT_RESERVE_INTERNAL_AMOUNT = 10000 * 1000L

  private var hot2ColdCancellable: Cancellable = null
  private var cold2HotCancellable: Cancellable = null
  val serializer = new ThriftBinarySerializer()
  val client: Option[RedisClient] = try {
    Some(new RedisClient(config.ip, config.port))
  } catch {
    case ex: Throwable => None
  }

  val delayinSeconds = 4
  override val processorId = BITWAY_PROCESSOR << supportedCurrency
  val channelToTransferProcessor = createChannelTo(ACCOUNT_TRANSFER_PROCESSOR <<) // DO NOT CHANGE

  val manager = new BitwayManager(supportedCurrency, config)

  override def preStart() = {
    super.preStart
    scheduleTryPour()
    scheduleSyncHotAddresses()
    initScheduleTransfer()
  }

  override def identifyChannel: PartialFunction[Any, String] = {
    case tc: TransferCryptoCurrency => "tsf"
    case mtc: MultiTransferCryptoCurrency => "tsf"
  }

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case TryFetchAddresses =>
      if (recoveryFinished && manager.isDryUp) {
        self ! FetchAddresses(supportedCurrency)
      } else {
        scheduleTryPour()
      }
    case TrySyncHotAddresses =>
      if (recoveryFinished) {
        self ! SyncHotAddresses(supportedCurrency)
      } else {
        scheduleSyncHotAddresses()
      }
    case FetchAddresses(currency) =>
      if (client.isDefined) {
        client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
          BitwayRequestType.GenerateAddress, currency, generateAddresses = Some(
            GenerateAddresses(config.batchFetchAddressNum)))))
      }
    case SyncHotAddresses(currency) =>
      if (client.isDefined) {
        client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
          BitwayRequestType.SyncHotAddresses, currency, syncHotAddresses = Some(
            SyncHotAddresses(supportedCurrency)))))
      }
    case TransferBetweenHotCold(txType) =>
      checkTransferHotCold(txType)

    case m @ CleanBlockChain(currency) =>
      persist(m) {
        event =>
          updateState(event)
      }
    case SyncPrivateKeys(currency, None) =>
      if (client.isDefined) {
        client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
          BitwayRequestType.SyncPrivateKeys, currency, syncPrivateKeys = Some(
            SyncPrivateKeys(supportedCurrency, Some(manager.getPubKeys()))))))
      }

    case m @ AdjustAddressAmount(currency, address, adjustAmount) =>
      if (manager.canAdjustAddressAmount(address, adjustAmount)) {
        persist(m) {
          event =>
            updateState(event)
            sender ! AdjustAddressAmountResult(
              supportedCurrency, ErrorCode.Ok, address, Some(manager.getAddressAmount(address)))
        }
      } else {
        sender ! AdjustAddressAmountResult(supportedCurrency, ErrorCode.InvalidAmount, address)
      }

    case m @ AllocateNewAddress(currency, _, _) =>
      val (address, needFetch) = manager.allocateAddress
      if (needFetch) self ! FetchAddresses(currency)
      if (address.isDefined) {
        persist(m.copy(assignedAddress = address)) {
          event =>
            updateState(event)
            sender ! AllocateNewAddressResult(supportedCurrency, ErrorCode.Ok, address, manager.address2NxtRsAddressMap.get(address.get))
        }
      } else {
        sender ! AllocateNewAddressResult(supportedCurrency, ErrorCode.NotEnoughAddressInPool, None)
      }

    // deprecated
    case p @ ConfirmablePersistent(m: TransferCryptoCurrency, _, _) =>
      confirm(p)
      sendTransferRequest(m)

    // deprecated
    case m: TransferCryptoCurrency =>
      sendTransferRequest(m)

    case p @ ConfirmablePersistent(m: MultiTransferCryptoCurrency, _, _) =>
      confirm(p)
      sendMultiTransferRequest(m)

    case m: MultiTransferCryptoCurrency =>
      sendMultiTransferRequest(m)

    case m @ BitwayMessage(currency, Some(res), None, None, None, None) =>
      if (res.error == ErrorCode.Ok) {
        persist(m) {
          event =>
            updateState(event)
        }
      } else {
        log.error("error occur when fetch addresses: " + res)
      }
    case m @ BitwayMessage(currency, None, Some(tx), None, None, None) =>
      val txWithTime = if (tx.timestamp.isDefined) tx else tx.copy(timestamp = Some(System.currentTimeMillis))
      if (tx.status == TransferStatus.Failed) {
        persist(m.copy(tx = Some(txWithTime))) {
          event =>
            channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(
              currency, List(tx))), transferProcessor.path)
        }
        reScheduleTransferForFailedTx(tx)
      } else {
        manager.completeCryptoCurrencyTransaction(tx) match {
          case None => log.debug("unrelated tx received")
          case Some(completedTx) =>
            if (manager.notProcessed(completedTx)) {
              persist(m.copy(tx = Some(completedTx))) {
                event =>
                  updateState(event)
                  channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(currency,
                    List(completedTx))), transferProcessor.path)
              }
              reScheduleTransferForTx(completedTx)
            }
        }
      }
    case m @ BitwayMessage(currency, None, None, Some(blockMsg), None, None) =>
      val continuity = manager.getBlockContinuity(blockMsg)
      // log.info("receive new block: " + blockMsg + " which recognizied as " + continuity)
      // log.info("maintained index list is: " + manager.getBlockIndexes)
      continuity match {
        case DUP => log.info("receive block which has been seen: " + blockMsg.block.index)
        case SUCCESSOR | REORG =>
          sendGetMissedBlockTime = 0
          val blocksMsgWithTime = if (blockMsg.timestamp.isDefined)
            blockMsg
          else blockMsg.copy(timestamp = Some(System.currentTimeMillis))
          persist(m.copy(blockMsg = Some(blocksMsgWithTime))) {
            event =>
              updateState(event)
              val relatedTxs = manager.extractTxsFromBlock(blockMsg.block)
              if (relatedTxs.nonEmpty) {
                val reorgIndex = if (continuity == REORG) {
                  log.info("reorg to index: " + blockMsg.reorgIndex)
                  log.info("maintained index list: " + manager.getBlockIndexes)
                  log.info("new block index: " + blockMsg.block.index)
                  blockMsg.reorgIndex
                } else None
                val msg = MultiCryptoCurrencyTransactionMessage(currency,
                  relatedTxs, reorgIndex)
                channelToTransferProcessor forward Deliver(Persistent(msg), transferProcessor.path)
                reScheduleTransferForBlock(msg)
              }
          }
        case GAP =>
          if (client.isDefined && System.currentTimeMillis - sendGetMissedBlockTime > RESEND_GET_MISSED_BLOCK_TIMEOUT) {
            sendGetMissedBlockTime = System.currentTimeMillis
            client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
              BitwayRequestType.GetMissedBlocks, currency, getMissedCryptoCurrencyBlocksRequest = Some(
                GetMissedCryptoCurrencyBlocks(manager.getBlockIndexes.get, blockMsg.block.index)))))
          }
        case OTHER_BRANCH =>
          throw new RuntimeException("The crypto currency seems has multi branches: " + currency)
        case BAD =>
          log.info("bad block received: " + blockMsg)
          log.info("maintained index list: " + manager.getBlockIndexes)
      }
    case m @ BitwayMessage(currency, None, None, None, Some(res), None) =>
      if (res.error == ErrorCode.Ok) {
        persist(m) {
          event =>
            updateState(event)
        }
      } else {
        log.error("error occur when sync hot addresses: " + res)
      }
    case m @ BitwayMessage(currency, None, None, None, None, Some(res)) =>
      if (res.error == ErrorCode.Ok) {
        persist(m) {
          event =>
            updateState(event)
        }
      } else {
        log.error("error occur when sync private keys: " + res)
      }
    case m @ DoRequestTransfer(t, _, _) =>
      persist(m) {
        event =>
          updateState(event)
          t.`type` match {
            case HotToCold => channelToTransferProcessor forward Deliver(Persistent(event), transferProcessor.path)
            case ColdToHot => channelToTransferProcessor forward Deliver(Persistent(event), transferProcessor.path)
            case _ =>
              log.error("Get unexpected DoRequestTransfer transfer type : " + event.toString)
          }
      }
  }

  private def sendTransferRequest(m: TransferCryptoCurrency) {
    if (client.isDefined) {
      val TransferCryptoCurrency(currency, infos, t) = m
      val (passedInfos, failedInfos, _) = partitionInfos(t, infos)
      if (passedInfos.size > 0) {
        if (failedInfos.size > 0) {
          sender ! TransferCryptoCurrencyResult(currency, ErrorCode.PartiallyFailed, Some(TransferCryptoCurrency(currency, failedInfos, t)))
        } else {
          sender ! TransferCryptoCurrencyResult(currency, ErrorCode.Ok, None)
        }
        client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
          BitwayRequestType.Transfer, currency, transferCryptoCurrency = Some(m.copy(transferInfos = passedInfos)))))
      } else {
        sender ! TransferCryptoCurrencyResult(currency, ErrorCode.AllFailed, Some(m.copy(transferInfos = failedInfos)))
      }
    }
  }

  private def sendMultiTransferRequest(m: MultiTransferCryptoCurrency) {
    if (client.isDefined) {
      val MultiTransferCryptoCurrency(currency, transferInfos) = m
      val passedInfos = collection.mutable.Map.empty[TransferType, Seq[CryptoCurrencyTransferInfo]]
      val failedInfos = collection.mutable.Map.empty[TransferType, Seq[CryptoCurrencyTransferInfo]]
      var hotAmount: Option[Long] = None
      // handle withdrawal before hotToCold by sorting transfers, avoid unnecessary hotToCold
      transferInfos.toSeq.sortBy(_._1.value).foreach {
        kv =>
          val (ps, fs, hm) = partitionInfos(kv._1, kv._2, hotAmount)
          hotAmount = hm
          if (ps.nonEmpty)
            passedInfos += kv._1 -> ps.toSeq
          if (fs.nonEmpty)
            failedInfos += kv._1 -> fs.toSeq
      }
      if (passedInfos.size > 0) {
        if (failedInfos.size > 0) {
          sender ! MultiTransferCryptoCurrencyResult(currency, ErrorCode.PartiallyFailed, Some(failedInfos))
        } else {
          sender ! MultiTransferCryptoCurrencyResult(currency, ErrorCode.Ok, None)
        }
        client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(BitwayRequestType.MultiTransfer, currency,
          multiTransferCryptoCurrency = Some(m.copy(transferInfos = passedInfos)))))
      } else {
        sender ! MultiTransferCryptoCurrencyResult(currency, ErrorCode.AllFailed, Some(failedInfos))
      }
    }
  }

  private def partitionInfos(transferType: TransferType, infos: Seq[CryptoCurrencyTransferInfo], hotAmount: Option[Long] = None): (List[CryptoCurrencyTransferInfo], List[CryptoCurrencyTransferInfo], Option[Long]) = {
    val passedInfos = collection.mutable.ListBuffer.empty[CryptoCurrencyTransferInfo]
    val failedInfos = collection.mutable.ListBuffer.empty[CryptoCurrencyTransferInfo]
    val (resInfos, isFail) = manager.completeTransferInfos(infos, transferType == HotToCold)
    var hotAmountRes: Option[Long] = hotAmount

    def subHotAmount(info: CryptoCurrencyTransferInfo) {
      val ha = hotAmountRes.getOrElse(manager.getAvailableReserveAmount(CryptoCurrencyAddressType.Hot, Some(config.confirmNum)))
      (info.internalAmount.get + HOT_RESERVE_INTERNAL_AMOUNT) > ha match {
        case true =>
          failedInfos.append(info.copy(error = Some(ErrorCode.InsufficientHot)))
        case _ =>
          passedInfos.append(info)
          hotAmountRes = Some(ha - info.internalAmount.get)
      }
    }

    transferType match {
      case HotToCold =>
        isFail match {
          case true => failedInfos ++= resInfos
          case _ => resInfos foreach subHotAmount
        }
      case Withdrawal =>
        resInfos foreach {
          resInfo =>
            manager.isWithdrawalToBadAddress(resInfo) match {
              case true =>
                failedInfos.append(resInfo.copy(error = Some(ErrorCode.WithdrawalToBadAddress)))
              case false =>
                subHotAmount(resInfo)
            }
        }
      case _ =>
        passedInfos ++= resInfos
    }

    (passedInfos.toList, failedInfos.toList, hotAmountRes)
  }

  private def reScheduleTransferForFailedTx(tx: CryptoCurrencyTransaction) {
    if (!config.enableHotColdTransfer) return
    tx.txType match {
      case Some(HotToCold) =>
        scheduleTransfer(HotToCold, config.hot2ColdTransferInterval)
      case _ =>
    }
  }

  private def reScheduleTransferForTx(tx: CryptoCurrencyTransaction) {
    if (!config.enableHotColdTransfer) return
    tx.txType match {
      case Some(ColdToHot) => // Find transaction for coldToHot, then postpone HotToCold check for half an hour
        scheduleTransfer(HotToCold, config.hot2ColdTransferInterval * 6)
      case _ =>
    }
  }

  private def reScheduleTransferForBlock(msg: MultiCryptoCurrencyTransactionMessage) {
    if (!config.enableHotColdTransfer) return
    msg.txs.foreach {
      tx =>
        tx.txType match {
          case Some(HotToCold) => scheduleTransfer(tx.txType.get, config.hot2ColdTransferInterval)
          case Some(ColdToHot) =>
            scheduleTransfer(tx.txType.get, config.cold2HotTransferInterval)
          case _ =>
        }
    }
  }

  private def scheduleTryPour() = {
    context.system.scheduler.scheduleOnce(delayinSeconds seconds, self, TryFetchAddresses)(context.system.dispatcher)
  }

  private def scheduleSyncHotAddresses() = {
    context.system.scheduler.scheduleOnce(delayinSeconds seconds, self, TrySyncHotAddresses)(context.system.dispatcher)
  }

  private def initScheduleTransfer() = {
    if (config.enableHotColdTransfer) {
      scheduleTransfer(HotToCold, config.hot2ColdTransferInterval)
      scheduleTransfer(ColdToHot, config.cold2HotTransferInterval)
    }
  }

  private def getRequestChannel = config.requestChannelPrefix + supportedCurrency.value.toString

  private def checkTransferHotCold(txType: TransferType) {
    if (config.enableHotColdTransfer) {
      transferHotColdIfNeed(txType)
    }
  }

  private def transferHotColdIfNeed(txType: TransferType) {
    var isTransfer = false
    manager.needHotColdTransfer match {
      case None =>
      case Some(amount) if amount == 0 =>
      case Some(amount) if amount > 0 && txType == HotToCold =>
        self ! DoRequestTransfer(AccountTransfer(0, 0, HotToCold, supportedCurrency, amount, created = Some(System.currentTimeMillis)))
        isTransfer = true
      case Some(amount) if amount < 0 && txType == ColdToHot =>
        self ! DoRequestTransfer(AccountTransfer(0, 0, ColdToHot, supportedCurrency, -amount, created = Some(System.currentTimeMillis)))
      case _ =>
    }
    txType match {
      case HotToCold =>
        scheduleTransfer(HotToCold, if (isTransfer) config.hot2ColdTransferIntervalLarge else config.hot2ColdTransferInterval)
      case ColdToHot =>
        scheduleTransfer(ColdToHot, config.cold2HotTransferInterval)
      case _ =>
        log.error(s"transferHotColdIfNeed get wrong txType: ${txType.toString}")
    }
  }

  private def scheduleTransfer(txType: TransferType, interval: FiniteDuration) {
    txType match {
      case HotToCold =>
        if (hot2ColdCancellable != null && !hot2ColdCancellable.isCancelled) hot2ColdCancellable.cancel()
        hot2ColdCancellable = context.system.scheduler.scheduleOnce(interval, self, TransferBetweenHotCold(HotToCold))(context.system.dispatcher)
      case ColdToHot =>
        if (cold2HotCancellable != null && !cold2HotCancellable.isCancelled) cold2HotCancellable.cancel()
        cold2HotCancellable = context.system.scheduler.scheduleOnce(interval, self, TransferBetweenHotCold(ColdToHot))(context.system.dispatcher)
      case _ =>
    }
  }

}

trait BitwayManagerBehavior {
  val manager: BitwayManager

  def updateState: Receive = {
    case AllocateNewAddress(currency, uid, Some(address)) => manager.addressAllocated(uid, address)

    case AdjustAddressAmount(currency, address, adjustAmount) => manager.adjustAddressAmount(address, adjustAmount)

    case BitwayMessage(currency, Some(res), None, None, None, None) =>
      if (res.addressType.isDefined && res.addresses.isDefined && res.addresses.get.size > 0)
        manager.faucetAddress(res.addressType.get, Set.empty[CryptoAddress] ++ res.addresses.get)

    case BitwayMessage(currency, None, Some(tx), None, None, None) =>
      if (tx.timestamp.isDefined) manager.updateLastAlive(tx.timestamp.get)
      manager.rememberTx(tx)

    case BitwayMessage(currency, None, None,
      Some(CryptoCurrencyBlockMessage(startIndex, block, timestamp)), None, None) =>
      if (timestamp.isDefined) manager.updateLastAlive(timestamp.get)
      manager.updateBlock(startIndex, block)

    case BitwayMessage(currency, None, None, None, Some(res), None) =>
      if (res.addresses.size > 0)
        manager.syncHotAddresses(Set.empty[CryptoAddress] ++ res.addresses)

    case BitwayMessage(currency, None, None, None, None, Some(res)) =>
      manager.syncPrivateKeys(res.addresses.toList)

    case CleanBlockChain(currency) =>
      manager.cleanBlockChain()

    case DoRequestTransfer(_, _, _) =>

    case e => println("bitway updateState doesn't handle the message: ", e)
  }
}

class BitwayReceiver(bitwayProcessor: ActorRef, supportedCurrency: Currency, config: BitwayConfig)
    extends Actor with ActorLogging {
  implicit val executeContext = context.system.dispatcher

  val serializer = new ThriftBinarySerializer()
  val client: Option[RedisClient] = try {
    Some(new RedisClient(config.ip, config.port))
  } catch {
    case ex: Throwable => None
  }

  override def preStart = {
    super.preStart
    listenAtRedis(10)
  }

  def receive = LoggingReceive {
    case ListenAtRedis =>
      if (client.isDefined) {
        client.get.lpop[Array[Byte]](getResponseChannel) match {
          case Some(s) =>
            val response = serializer.fromBinary(s, classOf[BitwayMessage.Immutable])
            bitwayProcessor ! response
            listenAtRedis()
          case None =>
            listenAtRedis(5)
        }
      }
  }

  def getResponseChannel = config.responseChannelPrefix + supportedCurrency.value.toString

  private def listenAtRedis(timeout: Long = 0) {
    context.system.scheduler.scheduleOnce(timeout seconds, self, ListenAtRedis)(context.system.dispatcher)
  }
}
