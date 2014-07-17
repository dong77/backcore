package com.coinport.bitway.NxtBitway

import scala.util.Random
import com.mongodb.casbah.MongoConnection

object NxtBitway {
  private val targetUrl="http://localhost:7876/nxt"
  private val mongocollection = MongoConnection("localhost", 27017)("coinex_bitway")("nxt")

  def main(args: Array[String]): Unit = {
    val mongoClient = new NxtMongoDAO(mongocollection)
    val httpClient = new NxtHttpClient(targetUrl)

    getAddress(10);

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
