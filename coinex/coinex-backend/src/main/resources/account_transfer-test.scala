import com.coinport.coinex.transfer.AccountTransferConfig
import com.coinport.coinex.data.Currency
import com.coinport.coinex.data.Currency._

new AccountTransferConfig {
  override val transferDebug = false
  override val confirmNumMap: collection.Map[Currency, Int] = collection.Map(Btc -> 1, Ltc -> 1, Doge -> 1, Bc -> 1, Drk -> 1, Vrc -> 1, Zet -> 1)
  override val manualCurrency = collection.Set[Currency](Btsx)
  override val succeededRetainNum: collection.Map[Currency, Int] = collection.Map(Btc -> 100, Ltc -> 200, Doge -> 300)
}
