import com.coinport.coinex.transfer.AccountTransferConfig
import com.coinport.coinex.data.Currency
import com.coinport.coinex.data.Currency._

new AccountTransferConfig {
  override val transferDebug = false
  override val confirmNumMap: collection.Map[Currency, Int] = collection.Map(Btc -> 1, Ltc -> 1, Doge -> 4, Bc -> 1, Drk -> 1, Vrc -> 1, Zet -> 1, Btsx -> 3, Nxt -> 1, Xrp -> 1)
  override val succeededRetainNum: collection.Map[Currency, Int] = collection.Map(Btc -> 100, Ltc -> 200, Doge -> 300)
  override val autoConfirmAmount: collection.Map[Currency, Long] =
    collection.Map(
      Btc -> 3E7.toLong,
      Ltc -> 10E8.toLong,
      Doge -> 1000E8.toLong,
      Bc -> 300E8.toLong,
      Drk -> 10E8.toLong,
      Vrc -> 20E8.toLong,
      Zet -> 1000E8.toLong,
      Btsx -> 20000E8.toLong,
      Nxt -> 100E8.toLong,
      Xrp -> 100E8.toLong)
  override val enableUsersToInner:collection.Map[Currency, Boolean] = collection.Map(Btc -> true, Ltc -> true, Doge -> false, Bc -> true, Drk -> true, Vrc -> true, Zet -> true, Btsx -> false, Nxt -> false, Xrp -> false)
}
