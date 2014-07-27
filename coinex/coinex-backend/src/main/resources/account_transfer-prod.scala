import com.coinport.coinex.transfer.AccountTransferConfig
import com.coinport.coinex.data.Currency
import com.coinport.coinex.data.Currency._

new AccountTransferConfig {
  override val transferDebug = false
  override val confirmNumMap: collection.Map[Currency, Int] = collection.Map(Btc -> 1, Ltc -> 4, Doge -> 4, Bc -> 10, Drk -> 4, Vrc -> 4, Zet -> 10, Btsx -> 50)
//  override val manualCurrency = collection.Set[Currency](Btsx)
  override val succeededRetainNum: collection.Map[Currency, Int] = collection.Map(Btc -> 100, Ltc -> 200, Doge -> 300)
}