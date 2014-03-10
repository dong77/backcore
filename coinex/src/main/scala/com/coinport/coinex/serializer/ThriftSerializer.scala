package com.coinport.coinex.serializer

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.domain._
import Currency._

// TODO
abstract class ThriftSerializer extends Serializer {

  // This is whether "fromBinary" requires a "clazz" or not
  def includeManifest: Boolean = true

  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 16 is reserved by Akka itself
  def identifier = 1234567

  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: DoDepositCash =>
      BinaryScalaCodec(DoDepositCash)(m)
    case _ => throw new IllegalArgumentException("talk to wangdong")
  }

  // "fromBinary" deserializes the given array,
  // using the type hint (if any, see "includeManifest" above)
  // into the optionally provided classLoader.
  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[DoDepositCash]=> BinaryScalaCodec(DoDepositCash).invert(bytes)
    case _ => throw new IllegalArgumentException("talk to wangdong")
  }
}