/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.event.LoggingReceive
import akka.persistence.Deliver
import akka.persistence.Persistent
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

object BitwayProcessor {
  final val INIT_FETCH_ADDRESS_NUM = 100

  // TODO(c): add embeded redis for unit test instead of disable the redis client
  //          inject the RedisClient instead of hardcode here
  val pullClient: Option[RedisClient] = try {
    Some(new RedisClient("localhost", 6379))
  } catch {
    case ex: Throwable => None
  }
  val pushClient: Option[RedisClient] = try {
    Some(new RedisClient("localhost", 6379))
  } catch {
    case ex: Throwable => None
  }
  val serializer = new ThriftBinarySerializer()

  def getRequestChannel(currency: Currency) = "creq_" + currency.toString.toLowerCase
  def getResponseChannel(currency: Currency) = "cres_" + currency.toString.toLowerCase

}

class BitwayProcessor(transferProcessor: ActorRef, supportedCurrency: Currency)
    extends ExtendedProcessor with EventsourcedProcessor with ActorLogging {

  import BitwayProcessor._
  import BlockContinuityEnum._

  val delayinSeconds = 4
  override val processorId = BITWAY_PROCESSOR << supportedCurrency
  val channelToTransferProcessor = createChannelTo(ACCOUNT_TRANSFER_PROCESSOR <<) // DO NOT CHANGE

  val manager = new BitwayManager(supportedCurrency)

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
    case FetchAddresses(currency) if pushClient.isDefined =>
      pushClient.get.rpush(getRequestChannel(supportedCurrency), serializer.toBinary(BitwayRequest(
        BitwayRequestType.GenerateAddress, currency, generateAddresses = Some(
          GenerateAddresses(INIT_FETCH_ADDRESS_NUM)))))

    case m @ GetNewAddress(currency, _) =>
      val (address, needFetch) = manager.allocateAddress
      if (needFetch) self ! FetchAddresses(currency)
      if (address.isDefined) {
        persist(m) { event =>
          updateState(event.copy(assignedAddress = address))
        }
        sender ! GetNewAddressResult(ErrorCode.Ok, address)
      } else {
        sender ! GetNewAddressResult(ErrorCode.NotEnoughAddressInPool, None)
      }

    case m @ TransferCryptoCurrency(currency, _, _) if pushClient.isDefined =>
      pushClient.get.rpush(getRequestChannel(supportedCurrency), serializer.toBinary(BitwayRequest(
        BitwayRequestType.Transfer, currency, transferCryptoCurrency = Some(m))))

    case m @ BitwayMessage(currency, Some(res), None, None) =>
      if (res.error == ErrorCode.Ok) {
        persist(res) { event =>
          updateState(m)
        }
      } else {
        log.error("error occur when fetch addresses: " + res)
      }
    case m @ BitwayMessage(currency, None, Some(tx), None) =>
      if (tx.status == CryptoCurrencyTransactionStatus.Failed) {
        channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(
          currency, List(tx))), transferProcessor.path)
      } else {
        manager.completeCryptoCurrencyTransaction(tx) match {
          case None => None
          case Some(completedTx) =>
            channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(currency,
              List(completedTx))), transferProcessor.path)
        }
      }
    case m @ BitwayMessage(currency, None, None, Some(blocksMsg)) =>
      val continuity = manager.getBlockContinuity(blocksMsg)
      continuity match {
        case DUP => log.info("receive block list which first block has seen: " + blocksMsg.blocks.head.index)
        case SUCCESSOR | REORG =>
          persist(m) { event =>
            updateState(event)
            val reorgIndex = if (continuity == REORG) blocksMsg.startIndex else None
            channelToTransferProcessor forward Deliver(Persistent(MultiCryptoCurrencyTransactionMessage(currency,
              manager.extractTxsFromBlocks(blocksMsg.blocks.toList), reorgIndex)), transferProcessor.path)
          }
        case GAP if pushClient.isDefined =>
          pushClient.get.rpush(getRequestChannel(supportedCurrency), serializer.toBinary(BitwayRequest(
            BitwayRequestType.GetMissedBlocks, currency, getMissedCryptoCurrencyBlocksRequest = Some(
              GetMissedCryptoCurrencyBlocks(manager.getBlockIndexes.get, blocksMsg.blocks.head.index)))))
        case OTHER_BRANCH =>
          throw new RuntimeException("The crypto currency seems has multi branches: " + currency)
      }
  }

  def updateState: Receive = {
    case GetNewAddress(currency, Some(address)) => manager.addressAllocated(address)
    case BitwayMessage(currency, Some(res), None, None) => manager.faucetAddress(
      res.addressType, Set.empty[String] ++ res.addresses)
    case BitwayMessage(currency, None, None, Some(CryptoCurrencyBlocksMessage(startIndex, blocks))) =>
      manager.appendBlockChain(blocks.map(_.index).toList, startIndex)
  }

  private def scheduleTryPour() = {
    context.system.scheduler.scheduleOnce(delayinSeconds seconds, self, TryFetchAddresses)(context.system.dispatcher)
  }
}

class BitwayReceiver(bitwayProcessor: ActorRef, supportedCurrency: Currency) extends Actor with ActorLogging {
  import BitwayProcessor._
  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    listenAtRedis()
  }

  def receive = LoggingReceive {
    case ListenAtRedis if pullClient.isDefined =>
      pullClient.get.blpop[String, Array[Byte]](1, getResponseChannel(supportedCurrency)) match {
        case Some(s) =>
          val response = serializer.fromBinary(s._2, classOf[BitwayMessage.Immutable])
          bitwayProcessor ! response
        case None => None
      }
      listenAtRedis()
  }

  private def listenAtRedis() {
    context.system.scheduler.scheduleOnce(0 seconds, self, ListenAtRedis)(context.system.dispatcher)
  }
}
