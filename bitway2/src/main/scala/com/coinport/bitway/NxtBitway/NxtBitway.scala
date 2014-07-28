package com.coinport.bitway.NxtBitway

import scala.util.Random
import com.mongodb.casbah.MongoConnection
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.{Config, ConfigFactory}
import com.coinport.bitway.NxtBitway.mongo.NxtMongoDAO
import com.coinport.bitway.NxtBitway.http.NxtHttpClient
import com.coinport.bitway.NxtBitway.processor.NxtProcessor
import com.coinport.bitway.NxtBitway.actor.NxtActor
import com.coinport.bitway.NxtBitway.StartupConfig
import com.redis.RedisClient

class NxtBitway(config: Config) {
  private val targetUrl="http://%s:%d/nxt".format(config.getString("nxtClient.host"), config.getInt("nxtClient.port"))
  private val mongocollection = MongoConnection(config.getString("mongodb.host"), config.getInt("mongodb.port"))("coinex_bitway")("nxt")

  val mongoClient = new NxtMongoDAO(mongocollection)
  val httpClient = new NxtHttpClient(targetUrl)
  val redisClient = new RedisClient(config.getString("redis.host"), config.getInt("redis.port"))

  def start(): Unit = {
    val nxtProcessor = new NxtProcessor(mongoClient, httpClient, redisClient)
    val system = ActorSystem.create()
    println()
    ActorSystem().actorOf(Props(new NxtActor(nxtProcessor, BitwayConfig())), name = "nxtReciever")
  }
}
