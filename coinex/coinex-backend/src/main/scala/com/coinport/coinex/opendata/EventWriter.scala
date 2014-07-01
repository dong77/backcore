package com.coinport.coinex.opendata

import akka.persistence.serialization.Snapshot
import java.io.Closeable
import org.apache.hadoop.fs.FileSystem

trait EventWriter {
  val BUFFER_SIZE = 2048

  def withStream[S <: Closeable, A](stream: S)(fun: S => A): A =
    try fun(stream) finally stream.close()

  def currentTime(): String = {
    val format = new java.text.SimpleDateFormat("yyyyMMddhhmmss")
    format.format(new java.util.Date(System.currentTimeMillis()))
  }
}

trait MessageWriter extends EventWriter {
  def writeMessages(processorId: String, lastSeqNum: Long, messages: List[(Long, Any)])(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem)
}

trait SnapshotWriter extends EventWriter {
  def writeSnapshot(processorId: String, seqNum: Long, snapshot: Snapshot)(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem)
}
