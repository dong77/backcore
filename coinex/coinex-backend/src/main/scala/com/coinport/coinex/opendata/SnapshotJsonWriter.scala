package com.coinport.coinex.opendata

import com.coinport.coinex.serializers._
import akka.persistence.serialization.Snapshot
import org.apache.hadoop.fs.{ Path, FileSystem }
import java.io.{ OutputStreamWriter, BufferedWriter }
import org.apache.commons.io.IOUtils

object SnapshotJsonWriter extends SnapshotWriter {

  override def writeSnapshot(processorId: String, seqNum: Long, snapshot: Snapshot)(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {

    val className = snapshot.data.getClass.getEnclosingClass.getSimpleName
    writeJsonSnapshot(config.jsonSnapshotDir, processorId, seqNum, snapshot, className, true)
    writeJsonSnapshot(config.debugSnapshotDir, processorId, seqNum, snapshot, className)
  }

  private def writeJsonSnapshot(outputDir: String, processorId: String, seqNum: Long, snapshot: Snapshot, className: String, isOpen: Boolean = false)(implicit config: OpenDataConfig, pFileMap: collection.mutable.Map[String, String], fs: FileSystem) {
    val serializer = isOpen match {
      case false => PrettyJsonSerializer
      case true if config.openSnapshotSerializerMap.contains(className) => config.openSnapshotSerializerMap(className)
      case _ => OpenDataJsonSerializer
    }
    val json = isOpen match {
      case true if config.openSnapshotFilterMap.contains(className) => serializer.toJson(config.openSnapshotFilterMap(className).filter(snapshot.data))
      case _ => serializer.toJson(snapshot.data)
    }
    val jsonSnapshot = s"""{"timestamp": ${System.currentTimeMillis()},\n"${className}": ${json}}"""
    val exportSnapshotPath = new Path(outputDir,
      s"coinport_snapshot_${pFileMap(processorId)}_${String.valueOf(seqNum).reverse.padTo(16, "0").reverse.mkString}_v1.json".toLowerCase)
    withStream(new BufferedWriter(new OutputStreamWriter(fs.create(exportSnapshotPath, true)), BUFFER_SIZE))(IOUtils.write(jsonSnapshot, _))
  }
}
