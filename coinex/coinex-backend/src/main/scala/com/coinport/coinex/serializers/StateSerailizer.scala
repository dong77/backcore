package com.coinport.coinex.serializers

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._
import Conversions._

class StateSerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 770607

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: AccountState =>
      val thrift = m.toThrift
      println("------StateSerializer>> toBinary for: " + thrift)
      BinaryScalaCodec(PersistentAccountState)(thrift)
    case m => throw new IllegalArgumentException("Cannot serialize object: " + m)
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[AccountState] =>
      val thrift = BinaryScalaCodec(PersistentAccountState).invert(bytes).get
      println("------StateSerializer>> fromBinary for: " + thrift)
      thrift.toPojo

    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName)
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString(""))
  }
}