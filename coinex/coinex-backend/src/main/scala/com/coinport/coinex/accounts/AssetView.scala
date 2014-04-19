/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import com.coinport.coinex.common.ExtendedView
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import TransferType._
import com.coinport.coinex.data.Implicits._
import com.coinport.coinex.common.PersistentId._

class AssetView extends ExtendedView {
  override val processorId = ACCOUNT_PROCESSOR <<
  override val viewId = USER_ASSET <<
  val manager = new AssetManager()

  def receive = LoggingReceive {
    case AdminConfirmTransferSuccess(t) =>
      t.`type` match {
        case Deposit => manager.updateAsset(t.updated.get, t.userId, t.currency, t.amount)
        case Withdrawal => manager.updateAsset(t.updated.get, t.userId, t.currency, -t.amount)
      }

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
      val historyPrice = HistoryPrice(manager.getHistoryPrice(q.from, q.to).map(x => x._1 -> x._2))
      val currentPrice = CurrentPrice(manager.getCurrentPrice)

      sender ! QueryAssetResult(currentAsset, historyAsset, currentPrice, historyPrice)
  }
}
