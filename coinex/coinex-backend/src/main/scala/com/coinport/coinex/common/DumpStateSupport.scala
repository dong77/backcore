package com.coinport.coinex.common

import akka.actor.Actor
import java.io.FileOutputStream
import akka.serialization.SerializationExtension
import akka.actor.ActorLogging

private[common] trait DumpStateSupport { self: Actor with ActorLogging =>
  def dumpToFile(state: AnyRef, file: String) = {
    val out = new FileOutputStream(file)
    val serialization = SerializationExtension(context.system)
    val serializer = serialization.findSerializerFor(state)
    try {
      out.write(serializer.toBinary(state))
      log.info("state dumped to file: " + file)
    } catch {
      case e: Throwable => log.error("Unable to dump state to file " + file, e)
    } finally {
      out.close()
    }
  }
}