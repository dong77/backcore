import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import scala.collection.immutable.Map
import Implicits._

Map((MARKET_UPDATE_PROCESSOR <<) -> "market", (ACCOUNT_PROCESSOR <<) -> "account", "p_m_btcrmb" -> "btc_cny")