package com.coinport.coinex.opendata

import org.apache.hadoop.fs.FileSystem

object MarketMessageWriter extends MessageWriter {

  def writeMessages(processorId: String, lastSeqNum: Long, messages: List[(Long, Any)])(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {

  }

}
