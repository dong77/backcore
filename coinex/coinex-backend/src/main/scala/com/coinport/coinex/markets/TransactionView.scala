/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.markets

import com.coinport.coinex.common._
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import akka.persistence.Persistent
import Implicits._
import com.mongodb.casbah._

class TransactionView(market: MarketSide, db: MongoDB) extends ExtendedView {
  override def processorId = "coinex_mup"
  override val viewId = "tx_view"

  private val coll = db("transaction_" + market.toString)
  private val manager = new TransactionManager(market, coll)

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    case Persistent(OrderSubmitted(orderInfo, txs), _) if orderInfo.side == market || orderInfo.side == market.reverse =>
      txs foreach (t => manager.addItem(t, orderInfo.side == market))

    case q: QueryTransaction =>
      sender ! QueryTransactionResult(manager().getItems(q), manager().countItems(q))
  }
}

class TransactionManager(market: MarketSide, coll: MongoCollection)
    extends Manager[TransactionDataState](TransactionDataState(coll)) {

  def addItem(t: Transaction, sameSide: Boolean) {
    val amount = Math.abs(t.takerUpdate.current.quantity - t.takerUpdate.previous.quantity)
    val reverseAmount = Math.abs(t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity)

    val price = if (sameSide) reverseAmount.toDouble / amount.toDouble else amount.toDouble / reverseAmount.toDouble

    val (taker, toId) = (t.takerUpdate.current.userId, t.takerUpdate.current.id)
    val (maker, moId) = (t.makerUpdate.current.userId, t.makerUpdate.current.id)

    val item = TransactionItem(tid = t.id, price = price, volume = amount, amount = reverseAmount, taker = taker,
      maker = maker, sameSide = sameSide, tOrder = toId, mOrder = moId, timestamp = t.timestamp)
    state.addItem(item)
  }
}
