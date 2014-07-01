package com.coinport.coinex.opendata

import java.io.{ OutputStreamWriter, BufferedWriter }
import org.apache.hadoop.fs.{ FileSystem, Path }
import com.coinport.coinex.serializers.PrettyJsonSerializer
import org.apache.hadoop.hbase.util.Bytes
import akka.persistence.hbase.common.Columns._

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
    val builder = new StringBuilder("{")
    for ((seqNum, msg) <- messages) {
      builder ++= "\"" ++= msg.getClass.getEnclosingClass.getSimpleName ++= "\":"
      builder ++= PrettyJsonSerializer.toJson(msg)
      builder ++= "\"" ++= Bytes.toString(SequenceNr) ++= "\":"
      builder ++= seqNum.toString
      builder ++= ","
    }
    builder.delete(builder.length - 1, builder.length)
    builder ++= "},"
    builder.toString()
  }

}
