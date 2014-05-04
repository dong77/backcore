/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence.Persistent
import akka.persistence.EventsourcedProcessor

import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import scala.concurrent.duration._
import scala.util.Random

import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.serializers._
import Implicits._

object BitwayProcessor {
  final val REQUEST_CHANNEL = "creq"
  final val RESPONSE_CHANNEL = "cres"
  final val INIT_FETCH_ADDRESS_NUM = 100

  // TODO(c): add embeded redis for unit test instead of disable the redis client
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
}

class BitwayProcessor extends ExtendedProcessor with EventsourcedProcessor with ActorLogging {

  import BitwayProcessor._

  val delayinSeconds = 4
  override val processorId = BITWAY_PROCESSOR <<

  val manager = new BitwayManager()

  override def preStart() = {
    super.preStart
    scheduleTryPour()
  }

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case TryFetchAddresses =>
      if (recoveryFinished) {
        manager.getSupportedCurrency.filter(manager.isDryUp).foreach { x =>
          self ! FetchAddresses(x)
        }
      } else {
        scheduleTryPour()
      }
    case FetchAddresses(currency) if pushClient.isDefined =>
      pushClient.get.rpush(REQUEST_CHANNEL, serializer.toBinary(BitwayRequest(BitwayType.GenerateAddress,
        Random.nextLong, currency, generateAddressRequest = Some(GenerateAddressRequest(INIT_FETCH_ADDRESS_NUM)))))

    case m @ GetNewAddress(currency, _) =>
      val (address, needFetch) = manager.allocateAddress(currency)
      if (needFetch) self ! FetchAddresses(currency)
      if (address.isDefined) {
        persist(m) { event =>
          updateState(event.copy(assignedAddress = address))
        }
        sender ! GetNewAddressResult(ErrorCode.Ok, address)
      } else {
        sender ! GetNewAddressResult(ErrorCode.NotEnoughAddressInPool, None)
      }

    case m @ BitwayResponse(t, id, currency, Some(res), None, None) =>
      println("~" * 40 + res)
    case m @ BitwayResponse(t, id, currency, None, Some(res), None) =>
      println("~" * 40 + res)
    case m @ BitwayResponse(t, id, currency, None, None, Some(res)) =>
      println("~" * 40 + res)
  }

  def updateState: Receive = {
    case GetNewAddress(currency, Some(address)) => manager.addressAllocated(currency, address)
  }

  private def scheduleTryPour() = {
    context.system.scheduler.scheduleOnce(delayinSeconds seconds, self, TryFetchAddresses)(context.system.dispatcher)
  }
}

class BitwayReceiver(bitwayProcessor: ActorRef) extends Actor with ActorLogging {
  import BitwayProcessor._
  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    listenAtRedis()
  }

  def receive = LoggingReceive {
    case ListenAtRedis if pullClient.isDefined =>
      pullClient.get.blpop[String, Array[Byte]](1, RESPONSE_CHANNEL) match {
        case Some(s) =>
          val response = serializer.fromBinary(s._2, classOf[BitwayResponse.Immutable])
          bitwayProcessor ! response
        case None => None
      }
      listenAtRedis()
  }

  private def listenAtRedis() {
    context.system.scheduler.scheduleOnce(0 seconds, self, ListenAtRedis)(context.system.dispatcher)
  }
}
