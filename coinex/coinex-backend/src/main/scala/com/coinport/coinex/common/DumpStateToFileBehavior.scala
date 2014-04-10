package com.coinport.coinex.common

import com.twitter.scrooge.ThriftStruct
import akka.actor.Actor
import akka.serialization.SerializationExtension
import java.io.FileOutputStream

trait DumpStateToFileBehavior { self: Actor =>
  def dump[T <: AnyRef](state: T): Unit = {
    val serialization = SerializationExtension(context.system)
    val serializer = serialization.findSerializerFor(state)
    val bytes = serializer.toBinary(state)

    val out = new FileOutputStream("/tmp/dump")
    try {
      out.write(bytes)
    } finally {
      out.close()
    }
  }
}