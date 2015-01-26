import com.coinport.coinex.transfer.AccountTransferConfig
import com.coinport.coinex.data.Currency
import com.coinport.coinex.data.Currency._

new AccountTransferConfig {
  override val transferDebug = false
  override val confirmNumMap: collection.Map[Currency, Int] = collection.Map(Btc -> 1, Ltc -> 4, Doge -> 4, Bc -> 10, Drk -> 4, Vrc -> 4, Zet -> 10, Btsx -> 50, Nxt -> 5, Xrp -> 1)
  override val succeededRetainNum: collection.Map[Currency, Int] = collection.Map(Btc -> 100, Ltc -> 200, Doge -> 300)
  override val manualCurrency: collection.Set[Currency] = collection.Set(Cny, Gooc)
  override val autoConfirmAmount: collection.Map[Currency, Long] =
    collection.Map(
      Btc -> 5E8.toLong,
      Ltc -> 600E8.toLong,
      Doge -> 5000000E8.toLong,
      Bc -> 50000E8.toLong,
      Drk -> 500E8.toLong,
      Vrc -> 5000E8.toLong,
      Zet -> 10000E8.toLong,
      Btsx -> 50000E8.toLong,
      Nxt -> 5000E8.toLong,
      Xrp -> 100000E8.toLong)
  override val enableUsersToInner:collection.Map[Currency, Boolean] = collection.Map(Btc -> true, Ltc -> true, Doge -> true, Bc -> true, Drk -> true, Vrc -> true, Zet -> true, Btsx -> false, Nxt -> false, Xrp -> false)
}