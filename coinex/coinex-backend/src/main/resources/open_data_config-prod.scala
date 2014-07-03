import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import com.coinport.coinex.opendata._
import scala.concurrent.duration._

import Implicits._

new OpenDataConfig {
  override val enableExportData = true
  override val pFileMap = scala.collection.mutable.Map((MARKET_UPDATE_PROCESSOR <<) -> "market", (ACCOUNT_PROCESSOR <<) -> "account", (ACCOUNT_TRANSFER_PROCESSOR <<) -> "deposit_withdrawal")
  com.coinport.coinex.CoinexApp.markets foreach { marketSide =>
    pFileMap += (MARKET_PROCESSOR << marketSide) -> ("market_" + marketSide.s)
  }
  override val scheduleInterval = 3600 second
  override val openSnapshotSerializerMap = Map.empty[String,com.coinport.coinex.serializers.BaseJsonSerializer]
  override val openSnapshotFilterMap = Map(TAccountState.Immutable.getClass.getEnclosingClass.getSimpleName -> TAccountStateFilter)
  override val snapshotWriterMap: Map[String, SnapshotWriter] = Map(TAccountState.Immutable.getClass.getEnclosingClass.getSimpleName -> AccountSnapshotCvsWriter)
}
