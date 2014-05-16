package com.coinport.coinex.opendata

import scala.concurrent.duration._

class OpenDataConfig() {
  val enableExportData: Boolean = false
  val pFileMap = collection.mutable.Map.empty[String, String]
  val snapshotHdfsDir: String = "/snapshot"
  val exportSnapshotHdfsDir: String = "/export/snapshot"
  val exportMessagesHdfsDir: String = "/export/messages"
  val hdfsHost: String = "hdfs://hadoop:54310"
  val scheduleInterval = 60 seconds // check if there are data to export every 1 minute.
}
