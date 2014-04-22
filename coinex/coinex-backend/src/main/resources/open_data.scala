import scala.collection.mutable.Map
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import Implicits._

/**
 * Created by liweichao on 14-4-21.
 */
Map((ROBOT_PROCESSOR <<) -> 0L, (MARKET_UPDATE_PROCESSOR <<) -> 0L, (METRICS_VIEW <<) -> 0L)