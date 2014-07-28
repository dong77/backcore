package com.coinport.bitway.NxtBitway

import com.redis.RedisClient
import com.coinport.coinex.data._
import com.coinport.coinex.serializers.ThriftBinarySerializer
import scala.Some

/**
 * Created by chenxi on 7/26/14.
 */

object NxtTest {
  val serializer = new ThriftBinarySerializer()
  val redisClient = new RedisClient("localhost", 6379)
  val requestChannel = "creq_" + Currency.Nxt.value.toString

  def main(args: Array[String]): Unit = {
    sendMoney()

  }

  def sendMoney() = {
    val ccti = CryptoCurrencyTransferInfo(id = 4, to = Some("NXT-QEL6-FK5E-ZDZU-4EY6U"), amount = Some(2.0), from = None)
    val tcc = TransferCryptoCurrency(currency = Currency.Nxt, `type` = TransferType.Withdrawal, transferInfos = Seq(ccti))
//    val ccti = CryptoCurrencyTransferInfo(id = 4, to = None, amount = Some(3.0), from = Some("NXT-NNCH-FHMH-BU3B-45LZV"))
//    val tcc = TransferCryptoCurrency(currency = Currency.Nxt, `type` = TransferType.UserToHot, transferInfos = Seq(ccti))
    val br = BitwayRequest(`type` = BitwayRequestType.Transfer, currency = Currency.Nxt, transferCryptoCurrency = Some(tcc))

    val s = serializer.toBinary(br)
    println("requestChannel", requestChannel)
    redisClient.rpush(requestChannel, s)
  }

  def genrateAddress() = {

    val ga = GenerateAddresses(20)
    val br = BitwayRequest(`type` = BitwayRequestType.GenerateAddress, currency = Currency.Nxt, generateAddresses = Some(ga))

    val s = serializer.toBinary(br)
    redisClient.rpush(requestChannel, s)
  }
}
