/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.actor.ActorLogging
import akka.actor.ActorRef
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
import com.coinport.coinex.serializers._
import Implicits._

class BitwayProcessor(transferProcessor: ActorRef, supportedCurrency: Currency, config: BitwayConfig)
    extends ExtendedProcessor with EventsourcedProcessor with BitwayManagerBehavior with ActorLogging {

  import BlockContinuityEnum._

  val serializer = new ThriftBinarySerializer()
  val client: Option[RedisClient] = try {
    Some(new RedisClient(config.ip, config.port))
  } catch {
    case ex: Throwable => None
  }

  val delayinSeconds = 4
  override val processorId = BITWAY_PROCESSOR << supportedCurrency
  val channelToTransferProcessor = createChannelTo(ACCOUNT_TRANSFER_PROCESSOR <<) // DO NOT CHANGE

  val manager = new BitwayManager(supportedCurrency, config.maintainedChainLength)

  override def preStart() = {
    super.preStart
    scheduleTryPour()
  }

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case TryFetchAddresses =>
      if (recoveryFinished && manager.isDryUp) {
        self ! FetchAddresses(supportedCurrency)
      } else {
        scheduleTryPour()
      }
    case FetchAddresses(currency) =>
      if (client.isDefined) {
        client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
          BitwayRequestType.GenerateAddress, currency, generateAddresses = Some(
            GenerateAddresses(config.batchFetchAddressNum)))))
      }

    case m @ AdjustAddressAmount(currency, address, adjustAmount) =>
      if (manager.canAdjustAddressAmount(address, adjustAmount)) {
        persist(m) { event =>
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
        persist(m.copy(assignedAddress = address)) { event =>
          updateState(event)
          sender ! AllocateNewAddressResult(supportedCurrency, ErrorCode.Ok, address)
        }
      } else {
        sender ! AllocateNewAddressResult(supportedCurrency, ErrorCode.NotEnoughAddressInPool, None)
      }

    case p @ ConfirmablePersistent(m @ TransferCryptoCurrency(currency, infos, t), _, _) =>
      if (client.isDefined) {
        confirm(p)
        val (completedInfos, isFail) = manager.completeTransferInfos(infos, t == TransferType.HotToCold)
        if (isFail) {
          sender ! TransferCryptoCurrencyResult(currency, ErrorCode.NoAddressFound)
        } else {
          sender ! TransferCryptoCurrencyResult(currency, ErrorCode.Ok)
          client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
            BitwayRequestType.Transfer, currency, transferCryptoCurrency = Some(m.copy(transferInfos = completedInfos)))))
        }
      }

    case m @ BitwayMessage(currency, Some(res), None, None) =>
      if (res.error == ErrorCode.Ok) {
        persist(m) { event =>
          updateState(event)
        }
      } else {
        log.error("error occur when fetch addresses: " + res)
      }
    case m @ BitwayMessage(currency, None, Some(tx), None) =>
      val txWithTime = if (tx.timestamp.isDefined) tx else tx.copy(timestamp = Some(System.currentTimeMillis))
      if (tx.status == TransferStatus.Failed) {
        persist(m.copy(tx = Some(txWithTime))) { event =>
          channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(
            currency, List(tx))), transferProcessor.path)
        }
      } else {
        manager.completeCryptoCurrencyTransaction(tx) match {
          case None => log.debug("unrelated tx received")
          case Some(completedTx) =>
            if (manager.notProcessed(completedTx)) {
              persist(m.copy(tx = Some(txWithTime))) { event =>
                updateState(event)
                channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(currency,
                  List(completedTx))), transferProcessor.path)
              }
            }
        }
      }
    case m @ BitwayMessage(currency, None, None, Some(blocksMsg)) =>
      val continuity = manager.getBlockContinuity(blocksMsg)
      continuity match {
        case DUP => log.info("receive block list which first block has seen: " + blocksMsg.blocks.head.index)
        case SUCCESSOR | REORG =>
          val blocksMsgWithTime = if (blocksMsg.timestamp.isDefined)
            blocksMsg else blocksMsg.copy(timestamp = Some(System.currentTimeMillis))
          persist(m.copy(blocksMsg = Some(blocksMsgWithTime))) { event =>
            updateState(event)
            val relatedTxs = manager.extractTxsFromBlocks(blocksMsg.blocks.toList)
            if (relatedTxs.nonEmpty) {
              val reorgIndex = if (continuity == REORG) blocksMsg.reorgIndex else None
              channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(currency,
                relatedTxs, reorgIndex)), transferProcessor.path)
            }
          }
        case GAP =>
          if (client.isDefined) {
            client.get.rpush(getRequestChannel, serializer.toBinary(BitwayRequest(
              BitwayRequestType.GetMissedBlocks, currency, getMissedCryptoCurrencyBlocksRequest = Some(
                GetMissedCryptoCurrencyBlocks(manager.getBlockIndexes.get, blocksMsg.blocks.head.index)))))
          }
        case OTHER_BRANCH =>
          throw new RuntimeException("The crypto currency seems has multi branches: " + currency)
      }
  }

  private def scheduleTryPour() = {
    context.system.scheduler.scheduleOnce(delayinSeconds seconds, self, TryFetchAddresses)(context.system.dispatcher)
  }

  private def getRequestChannel = config.requestChannelPrefix + supportedCurrency.toString.toLowerCase
}

trait BitwayManagerBehavior {
  val manager: BitwayManager

  def updateState: Receive = {
    case AllocateNewAddress(currency, uid, Some(address)) => manager.addressAllocated(uid, address)
    case AdjustAddressAmount(currency, address, adjustAmount) => manager.adjustAddressAmount(address, adjustAmount)
    case BitwayMessage(currency, Some(res), None, None) =>
      if (res.addressType.isDefined && res.addresses.isDefined && res.addresses.get.size > 0)
        manager.faucetAddress(res.addressType.get, Set.empty[String] ++ res.addresses.get)
    case BitwayMessage(currency, None, Some(tx), None) =>
      if (tx.timestamp.isDefined) manager.updateLastAlive(tx.timestamp.get)
      manager.rememberTx(tx)
    case BitwayMessage(currency, None, None, Some(CryptoCurrencyBlocksMessage(startIndex, blocks, timestamp))) =>
      if (timestamp.isDefined) manager.updateLastAlive(timestamp.get)
      manager.updateBlocks(startIndex, blocks)
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
    listenAtRedis()
  }

  def receive = LoggingReceive {
    case ListenAtRedis =>
      if (client.isDefined) {
        client.get.blpop[String, Array[Byte]](1, getResponseChannel) match {
          case Some(s) =>
            val response = serializer.fromBinary(s._2, classOf[BitwayMessage.Immutable])
            bitwayProcessor ! response
          case None => None
        }
        listenAtRedis()
      }
  }

  def getResponseChannel = config.responseChannelPrefix + supportedCurrency.toString.toLowerCase

  private def listenAtRedis() {
    context.system.scheduler.scheduleOnce(0 seconds, self, ListenAtRedis)(context.system.dispatcher)
  }
}
