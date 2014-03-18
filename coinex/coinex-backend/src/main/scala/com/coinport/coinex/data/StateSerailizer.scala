package com.coinport.coinex.data

import akka.serialization.Serializer
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.coinport.coinex.data._
import Conversions._

class StateSerializer extends Serializer {
  val includeManifest: Boolean = true
  val identifier = 770607

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: AccountState => BinaryScalaCodec(PersistentAccountState)(m.toThrift)
    case m => throw new IllegalArgumentException("Cannot serialize object: " + m)
  }

  def fromBinary(bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = clazz match {
    case Some(c) if c == classOf[AccountState] => BinaryScalaCodec(PersistentAccountState).invert(bytes).get.toPojo

    case Some(c) => throw new IllegalArgumentException("Cannot deserialize class: " + c.getCanonicalName)
    case None => throw new IllegalArgumentException("No class found in EventSerializer when deserializing array: " + bytes.mkString(""))
  }
}