package com.coinport.coinex.common

import akka.actor.Actor
import java.io.FileOutputStream
import akka.serialization.SerializationExtension

// TODO(d): dump as Json?
trait DumpStateSupport { self: Actor =>
  def dumpToFile(state: AnyRef, file: String) = {
    val out = new FileOutputStream(file)
    val serialization = SerializationExtension(context.system)
    val serializer = serialization.findSerializerFor(state)
    try {
      out.write(serializer.toBinary(state))
    } finally {
      out.close()
    }
  }
}