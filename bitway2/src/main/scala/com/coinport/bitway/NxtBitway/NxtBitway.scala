package com.coinport.bitway.NxtBitway

import scala.util.Random
import com.mongodb.casbah.MongoConnection
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object NxtBitway {
  private val targetUrl="http://localhost:7876/nxt"
  private val mongocollection = MongoConnection("localhost", 27017)("coinex_bitway")("nxt")
  val mongoClient = new NxtMongoDAO(mongocollection)
  val httpClient = new NxtHttpClient(targetUrl)
//  val defaultAkkaConfig = "akka.conf"
//  val akkaConfigProp = System.getProperty("akka.config")
//  val akkaConfigResource = if (akkaConfigProp != null) akkaConfigProp else defaultAkkaConfig
//  println("=" * 20 + "  Akka config  " + "=" * 20)
//  println("  conf/" + akkaConfigResource)
//  println("=" * 55)
//  val config = ConfigFactory.load(akkaConfigResource)


  def start(): Unit = {
    //listen backend message
    val nxtReciever = ActorSystem().actorOf(Props(new NxtReceiver(BitwayConfig())), name = "nxtReciever")
    nxtReciever ! ListenAtRedis

    //monitor the nxt block transaction etc.
    val nxtMonitor = ActorSystem().actorOf(Props(new NxtMonitor(BitwayConfig())), name = "nxtMonitor")
    nxtMonitor ! MonitorAtHttp

    def getAddress(addressNum: Int) = {
      val secretSeq = generateSecret(addressNum)
      val nxts = httpClient.getMultiAddresses(secretSeq)
      mongoClient.insertAddresses(nxts)
    }

    def generateSecret(addressNum: Int): Seq[String] = {
      val rand = new Random()
      val count = mongoClient.countAddress()
      (0 until addressNum).map{ i =>
        "www.coinport.com" + "%%%" + rand.nextString(10) +
        (count + i) + "%%%" + rand.nextString(10) +
          System.currentTimeMillis() + "%%%" + rand.nextString(10)
      }.toSeq
    }
  }
}
