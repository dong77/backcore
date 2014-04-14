package com.coinport.coinex.accounts

import org.specs2.mutable.Specification
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits._

class AssetManagerSpec extends Specification {
  "UserAssetSpec" should {
    val today = 1397486565673L
    val dayAfterToday = today + 1000 * 3600 * 24
    "update user asset and can get them all" in {
      val manager = new AssetManager()

      manager.updateAsset(1L, today, Rmb, 1000)
      manager.updateAsset(1L, today, Btc, 1000)

      manager.updateAsset(1L, dayAfterToday, Pts, 1000)
      manager.updateAsset(1L, dayAfterToday, Ltc, 1000)
      manager.updateAsset(1L, dayAfterToday, Rmb, 1000)

      manager.getAsset(1L, 0, dayAfterToday) mustEqual
        Map(16174 -> Map(Btc -> 1000, Rmb -> 1000), 16175 -> Map(Rmb -> 1000, Ltc -> 1000, Pts -> 1000))
    }

    "update price of currency and can get them all" in {
      val manager = new AssetManager()

      manager.updatePrice(Btc ~> Rmb, today, 3000)
      manager.updatePrice(Ltc ~> Rmb, today, 100)

      manager.updatePrice(Btc ~> Rmb, dayAfterToday, 4000)
      manager.updatePrice(Ltc ~> Rmb, dayAfterToday, 200)

      manager.getPrice(0, dayAfterToday) mustEqual
        Map(Btc ~> Rmb -> Map(16174 -> 3000, 16175 -> 4000), Ltc ~> Rmb -> Map(16174 -> 100, 16175 -> 200))
    }
  }
}
