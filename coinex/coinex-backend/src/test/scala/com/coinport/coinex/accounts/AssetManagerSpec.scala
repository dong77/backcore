package com.coinport.coinex.accounts

import org.specs2.mutable.Specification
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits._

class AssetManagerSpec extends Specification {
  "UserAssetSpec" should {
    val day = 1397486565673L
    val day2 = day + 1000 * 3600 * 24
    val day3 = day2 + 1000 * 3600 * 24
    "update user asset and can get them all" in {
      val manager = new AssetManager()

      manager.updateAsset(1L, day, Rmb, 1000)
      manager.updateAsset(1L, day, Btc, 1000)

      manager.updateAsset(1L, day2, Pts, 1000)
      manager.updateAsset(1L, day2, Ltc, 1000)
      manager.updateAsset(1L, day2, Rmb, 1000)

      manager.updateAsset(1L, day3, Pts, -500)
      manager.updateAsset(1L, day3, Rmb, -500)

      //      println(manager.historyAssetMap)
      manager.getHistoryAsset(1L, 0, day2) mustEqual
        Map(16174 -> Map(Btc -> 1000, Rmb -> 1000), 16175 -> Map(Rmb -> 1000, Ltc -> 1000, Pts -> 1000))

      manager.getCurrentAsset(1L) mustEqual
        Map(Rmb -> 1500, Ltc -> 1000, Btc -> 1000, Pts -> 500)
    }

    "update price of currency and can get them all" in {
      val manager = new AssetManager()

      manager.updatePrice(Btc ~> Rmb, day, 3000)
      manager.updatePrice(Ltc ~> Rmb, day2, 200)
      manager.updatePrice(Pts ~> Rmb, day3, 100)
      manager.updatePrice(Btc ~> Rmb, day3, 4000)

      manager.getHistoryPrice(0, day3) mustEqual
        Map(Btc ~> Rmb -> Map(16174 -> 3000, 16176 -> 4000), Ltc ~> Rmb -> Map(16175 -> 200), Pts ~> Rmb -> Map(16176 -> 100))
      manager.getCurrentPrice mustEqual Map(Btc ~> Rmb -> 4000, Ltc ~> Rmb -> 200, Pts ~> Rmb -> 100)
    }
  }
}
