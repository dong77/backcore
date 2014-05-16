import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.opendata.OpenDataConfig
import scala.concurrent.duration._

import Implicits._

new OpenDataConfig {
  override val enableExportData = true
  override val pFileMap = scala.collection.mutable.Map((MARKET_UPDATE_PROCESSOR <<) -> "market", (ACCOUNT_PROCESSOR <<) -> "account")
  com.coinport.coinex.CoinexApp.markets foreach { marketSide =>
    pFileMap += (MARKET_PROCESSOR << marketSide) -> ("market_" + marketSide.s)
  }
  override val snapshotHdfsDir = "/snapshot/"
  override val exportSnapshotHdfsDir = "/export/snapshot/"
  override val exportMessagesHdfsDir = "/export/messages/"
  override val hdfsHost = "hdfs://hadoop:54310"
  override val scheduleInterval = 60 second // check every minute
}
