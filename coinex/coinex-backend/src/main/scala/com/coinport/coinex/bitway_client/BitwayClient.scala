/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway_client

import akka.actor._
import akka.event.LoggingReceive
import com.redis._

import com.coinport.coinex.data._

object BitwayClient {
  val client = new RedisClient("localhost", 6379)
}

class BitwayPublisher(client: RedisClient) extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case _ => None
  }
}

class BitwayReceiver(client: RedisClient) extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case request @ GenerateWalletRequest(currency) =>
      client.rpush("list-1", "foo")
  }
}
