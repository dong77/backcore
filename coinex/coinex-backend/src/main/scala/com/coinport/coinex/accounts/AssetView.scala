/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import com.coinport.coinex.fee.FeeConfig
import com.coinport.coinex.common.ExtendedView
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import com.coinport.coinex.data.Implicits._

class AssetView(val feeConfig: FeeConfig) extends ExtendedView {
  override val processorId = "coinex_ap"
  override val viewId = "coinex_asset_view"
  val manager = new AssetManager()

  def receive = LoggingReceive {
    case a: AdminConfirmCashDepositSuccess =>
      manager.updateAsset(a.deposit.updated.get, a.deposit.userId, a.deposit.currency, a.deposit.amount)
    case a: AdminConfirmCashWithdrawalSuccess =>
      manager.updateAsset(a.withdrawal.updated.get, a.withdrawal.userId, a.withdrawal.currency, -a.withdrawal.amount)
    case OrderSubmitted(originOrderInfo, txs) =>
      if (!txs.isEmpty) {
        val side = originOrderInfo.side

        val toutCurrency = side.outCurrency
        val tinCurrency = side.inCurrency

        val taker = originOrderInfo.order.userId
        val timestamp = originOrderInfo.lastTxTimestamp.get

        manager.updateAsset(taker, timestamp, tinCurrency, originOrderInfo.inAmount)
        manager.updateAsset(taker, timestamp, toutCurrency, -originOrderInfo.outAmount)

        txs foreach {
          tx =>
            val min = tx.takerUpdate.previous.quantity - tx.takerUpdate.current.quantity
            val mout = tx.makerUpdate.previous.quantity - tx.makerUpdate.current.quantity

            val maker = tx.takerUpdate.current.userId
            val timestamp2 = tx.timestamp

            manager.updateAsset(maker, timestamp2, toutCurrency, min)
            manager.updateAsset(maker, timestamp2, tinCurrency, -mout)
        }

        manager.updatePrice(side, timestamp, 1 / txs.last.makerUpdate.current.price.get)
        manager.updatePrice(side.reverse, timestamp, txs.last.makerUpdate.current.price.get)
      }
    case q: QueryAsset =>
      val userAssets = manager.getAsset(q.uid, q.from, q.to).map(x => x._1 -> UserAsset(x._2))
      val marketPrice = MarketPrice(manager.getPrice(q.from, q.to).map(x => x._1 -> TimePrice(x._2)))
      sender ! QueryAssetResult(userAssets, marketPrice)
  }
}
