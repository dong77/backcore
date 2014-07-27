package com.coinport.bitway.NxtBitway

import com.redis.RedisClient
import com.coinport.coinex.data._
import com.coinport.coinex.serializers.ThriftBinarySerializer
import scala.Some

/**
 * Created by chenxi on 7/26/14.
 */

object NxtTest {

  def main(args: Array[String]): Unit = {
    val serializer = new ThriftBinarySerializer()
    val redisClient = new RedisClient("localhost", 6379)
    val requestChannel = "creq_" + Currency.Nxt.value.toString

    val ccti = CryptoCurrencyTransferInfo(id = 3, to = None, amount = Some(1.0), from = Some("NXT-ZNVJ-WBMH-42MP-9N6AS"))
    val tcc = TransferCryptoCurrency(currency = Currency.Nxt, `type` = TransferType.UserToHot, transferInfos = Seq(ccti))
    val br = BitwayRequest(`type` = BitwayRequestType.Transfer, currency = Currency.Nxt, transferCryptoCurrency = Some(tcc))

    val s = serializer.toBinary(br)
    println("requestChannel", requestChannel)
    redisClient.rpush(requestChannel, s)
  }
}
