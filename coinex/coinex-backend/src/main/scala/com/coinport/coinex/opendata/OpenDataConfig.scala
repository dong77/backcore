package com.coinport.coinex.opendata

import scala.concurrent.duration._
import com.coinport.coinex.serializers.BaseJsonSerializer
import com.coinport.coinex.data.{ CryptoCurrencyTransactionPort, RedeliverFilterData, RedeliverFilters, TAccountTransferState }

class OpenDataConfig() {
  val enableExportData: Boolean = false
  val pFileMap = collection.mutable.Map.empty[String, String]
  val snapshotHdfsDir: String = "/snapshot"
  val exportSnapshotHdfsDir: String = "/export/snapshot"
  val exportMessagesHdfsDir: String = "/export/messages"
  val debugSnapshotHdfsDir: String = "/debug/snapshot"
  val hdfsHost: String = "hdfs://hadoop:54310"
  val scheduleInterval = 60 seconds // check if there are data to export every 1 minute.
  val openSnapshotSerializerMap: Map[String, BaseJsonSerializer] = Map.empty[String, BaseJsonSerializer]
  val openSnapshotFilterMap: Map[String, BaseJsonFilter] = Map.empty[String, BaseJsonFilter]
}

trait BaseJsonFilter {
  def filter(original: Any): Any
}

object TAccountTransferStateFilter extends BaseJsonFilter {
  override def filter(original: Any): Any = {
    original.asInstanceOf[TAccountTransferState.Immutable].copy(
      filters = RedeliverFilters(filterMap = Map.empty[String, RedeliverFilterData]),
      depositSigId2TxPortIdMapInner = Map.empty[String, Map[CryptoCurrencyTransactionPort, Long]]
    )
  }
}