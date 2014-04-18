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
import com.coinport.coinex.common.PersistentId._

class AssetView extends ExtendedView {
  override val processorId = ACCOUNT_PROCESSOR <<
  override val viewId = USER_ASSET <<
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
      val historyAsset = HistoryAsset(manager.getHistoryAsset(q.uid, q.from, q.to))
      val currentAsset = CurrentAsset(manager.getCurrentAsset(q.uid))
      val marketPrice = MarketPrice(manager.getPrice(q.from, q.to).map(x => x._1 -> HistoryPrice(x._2)))
      sender ! QueryAssetResult(currentAsset, historyAsset, marketPrice)
  }
}
