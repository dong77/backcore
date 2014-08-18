import com.coinport.coinex.data.Currency
import com.coinport.coinex.transfer.AccountTransferConfig
import com.coinport.coinex.data.Currency._

new AccountTransferConfig {
  override val transferDebug = true
  override val confirmNumMap: collection.Map[Currency, Int] = collection.Map(Btc -> 2, Ltc -> 2, Doge -> 2)
  override val succeededRetainNum: collection.Map[Currency, Int] = collection.Map(Btc -> 100, Ltc -> 200, Doge -> 300)
  override val autoConfirmAmount: collection.Map[Currency, Long] =
    collection.Map(
      Btc -> 3E7.toLong,
      Ltc -> 10E8.toLong,
      Doge -> 10E8.toLong,
      Bc -> 300E8.toLong,
      Drk -> 10E8.toLong,
      Vrc -> 20E8.toLong,
      Zet -> 1000E8.toLong,
      Btsx -> 20000E8.toLong,
      Nxt -> 100E8.toLong)
}