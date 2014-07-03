package com.coinport.coinex.opendata

import com.coinport.coinex.serializers.BaseJsonSerializer
import com.coinport.coinex.data.TAccountState
import scala.concurrent.duration._

class OpenDataConfig() {
  val enableExportData: Boolean = false
  val pFileMap = collection.mutable.Map.empty[String, String]
  val snapshotHdfsDir: String = "/snapshot"
  val jsonSnapshotDir: String = "/export/snapshot"
  val jsonMessagesDir: String = "/export/messages"
  val debugSnapshotDir: String = "/debug/snapshot"
  val hdfsHost: String = "hdfs://hadoop:54310"
  val scheduleInterval = 60 seconds // check if there are data to export every 1 minute.
  val openSnapshotSerializerMap: Map[String, BaseJsonSerializer] = Map.empty
  val openSnapshotFilterMap: Map[String, BaseJsonFilter] = Map.empty

  val csvAssetDir = "/export/csv/asset"
  val csvDwDir = "/export/csv/dw"
  val csvTxDir = "/export/csv/tx"
  val snapshotWriterMap: Map[String, SnapshotWriter] = Map.empty[String, SnapshotWriter]
  val messageWriterMap: collection.mutable.Map[String, MessageWriter] = collection.mutable.Map.empty[String, MessageWriter]
}

trait BaseJsonFilter {
  def filter(original: Any): Any
}

object TAccountStateFilter extends BaseJsonFilter {
  override def filter(original: Any): Any = {
    original.asInstanceOf[TAccountState.Immutable].copy(
      codeAIndexMap = Map.empty,
      codeBIndexMap = Map.empty
    )
  }
}