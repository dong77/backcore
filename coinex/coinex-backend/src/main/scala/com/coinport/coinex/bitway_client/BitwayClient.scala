/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway_client

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence.Persistent
import com.redis._
import com.redis.serialization.Parse.Implicits.parseByteArray
import scala.concurrent.duration._

import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.serializers._
import Implicits._

object BitwayClient {
  final val REQUEST_CHANNEL = "creq"
  final val RESPONSE_CHANNEL = "cres"

  // TODO(c): add embeded redis for unit test instead of disable the redis client
  val client: Option[RedisClient] = try {
    Some(new RedisClient("localhost", 6379))
  } catch {
    case ex: Throwable => None
  }
  val serializer = new ThriftBinarySerializer()
}

class BitwayProcessor() extends ExtendedProcessor with ActorLogging {

  override val processorId = BITWAY_PROCESSOR <<

  def receive = LoggingReceive {
    case Persistent(BitwayResponse(t, id, currency, Some(res), None, None), _) =>
      println("~" * 40 + res)
    case Persistent(BitwayResponse(t, id, currency, None, Some(res), None), _) =>
      println("~" * 40 + res)
    case Persistent(BitwayResponse(t, id, currency, None, None, Some(res)), _) =>
      println("~" * 40 + res)
  }
}

class BitwayReceiver(bitwayProcessor: ActorRef) extends Actor with ActorLogging {
  import BitwayClient._
  implicit val executeContext = context.system.dispatcher

  override def preStart = {
    super.preStart
    listenAtRedis()
  }

  def receive = LoggingReceive {
    case ListenAtRedis if client.isDefined =>
      client.get.blpop[String, Array[Byte]](1, RESPONSE_CHANNEL) match {
        case Some(s) =>
          val response = serializer.fromBinary(s._2, classOf[BitwayResponse.Immutable])
          bitwayProcessor ! Persistent(response)
        case None => None
      }
      listenAtRedis()
  }

  private def listenAtRedis() {
    context.system.scheduler.scheduleOnce(0 seconds, self, ListenAtRedis)
  }
}
