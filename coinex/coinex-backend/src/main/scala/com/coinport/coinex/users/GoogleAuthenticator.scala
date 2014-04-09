package com.coinport.coinex.users

import java.security._
import org.apache.commons.codec.binary.Base32
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import scala.collection.mutable.ArrayBuffer
import java.nio.ByteBuffer

final class GoogleAuthenticator {
  private val rand = SecureRandom.getInstance("SHA1PRNG", "SUN")

  def createSecret = {
    val buffer = new Array[Byte](10)
    rand.nextBytes(buffer)
    new String(new Base32().encode(buffer));
  }

  def getTimeIndex(millis: Long = System.currentTimeMillis) = millis / 30000

  def verifyCode(secret: String, code: Int, timeIndex: Long, variance: Int): Boolean = {
    val secretBytes = new Base32().decode(secret)
    (-variance to variance) map { i =>

      println("code: " + getCode(secretBytes, timeIndex + i))
      if (getCode(secretBytes, timeIndex + i) == code) { return true; }
    }
    false
  }

  def getCode(secret: Array[Byte], timeIndex: Long): Int = {
    val buffer = ByteBuffer.allocate(8)
    buffer.putLong(timeIndex)
    val timeBytes = buffer.array()

    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(secret, "HmacSHA1"))
    val hash = mac.doFinal(timeBytes)

    val offset = hash(19) & 0xf
    var truncatedHash: Long = hash(offset) & 0x7f
    (1 to 3) foreach { i =>
      truncatedHash <<= 8
      truncatedHash |= hash(offset + i) & 0xff
    }
    (truncatedHash % 1000000).toInt
  }
}