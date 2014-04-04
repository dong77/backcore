package com.coinport.coinex.ot

import akka.actor.{ Actor, ActorLogging }
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import akka.event.LoggingReceive

class TransactionReader(db: MongoDB) extends Actor with TransactionMongoHandler with ActorLogging {

  val coll = db("transaction")

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("TransactionReader")

    case q: QueryTransaction =>
      sender ! QueryTransactionResult(getItems(q), countItems(q))
  }
}

class TransactionWriter(db: MongoDB) extends Actor with TransactionMongoHandler with ActorLogging {

  val coll = db("transaction")

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("TransactionWriter")

    case OrderSubmitted(orderInfo, txs) =>
      txs foreach { t =>
        val amount = Math.abs(t.takerUpdate.current.quantity - t.takerUpdate.previous.quantity)
        val reverseAmount = Math.abs(t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity)

        val price = reverseAmount.toDouble / amount.toDouble

        val (taker, toId) = (t.takerUpdate.current.userId, t.takerUpdate.current.id)
        val (maker, moId) = (t.makerUpdate.current.userId, t.makerUpdate.current.id)

        val item = TransactionItem(tid = t.id, price = price, volume = amount, amount = reverseAmount, taker = taker,
          maker = maker, tOrder = toId, mOrder = moId, side = t.side, timestamp = t.timestamp)
        addItem(item)
      }
  }
}
