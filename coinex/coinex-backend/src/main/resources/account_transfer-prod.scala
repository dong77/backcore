import com.coinport.coinex.transfer.AccountTransferConfig
import com.coinport.coinex.data.Currency
import com.coinport.coinex.data.Currency._

new AccountTransferConfig {
  override val transferDebug = false
  override val confirmNumMap: collection.Map[Currency, Int] = collection.Map(Btc -> 1, Ltc -> 4, Doge -> 4, Bc -> 10, Drk -> 4, Vrc -> 4, Zet -> 10, Btsx -> 50, Nxt -> 5)
  override val succeededRetainNum: collection.Map[Currency, Int] = collection.Map(Btc -> 100, Ltc -> 200, Doge -> 300)
  override val autoConfirmAmount: collection.Map[Currency, Long] =
    collection.Map(
      Btc -> 2E8.toLong,
      Ltc -> 40E8.toLong,
      Doge -> 1000000E8.toLong,
      Bc -> 1000E8.toLong,
      Drk -> 15E8.toLong,
      Vrc -> 500E8.toLong,
      Zet -> 2000E8.toLong,
      Btsx -> 100000E8.toLong,
      Nxt -> 1000E8.toLong)
}