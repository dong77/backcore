package com.coinport.coinex.opendata

import akka.persistence.hbase.common.Columns._
import com.coinport.coinex.serializers.PrettyJsonSerializer
import java.io.{ OutputStreamWriter, BufferedWriter }
import org.apache.hadoop.fs.{ FileSystem, Path }
import org.apache.hadoop.hbase.util.Bytes

object MessageJsonWriter extends MessageWriter {

  override def writeMessages(processorId: String, lastSeqNum: Long, messages: List[(Long, Any)])(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {
    val writer = new BufferedWriter(new OutputStreamWriter(fs.create(
      new Path(config.jsonMessagesDir, s"coinport_events_${pFileMap(processorId)}_${String.valueOf(lastSeqNum).reverse.padTo(16, "0").reverse.mkString}_v1.json".toLowerCase))))
    writer.write(s"""{"timestamp": ${System.currentTimeMillis()},\n"events": [""")
    val data = getMessages(messages)
    writer.write(data.substring(0, data.length - 1))
    writer.write("]}")
    writer.flush()
    writer.close()
  }

  private def getMessages(messages: List[(Long, Any)]): String = {
    val builder = new StringBuilder()
    for ((seqNum, msg) <- messages) {
      builder ++= s"""{"${msg.getClass.getEnclosingClass.getSimpleName}":${PrettyJsonSerializer.toJson(msg)},"${Bytes.toString(SequenceNr)}":${seqNum.toString}},"""
    }
    builder.toString()
  }

}
