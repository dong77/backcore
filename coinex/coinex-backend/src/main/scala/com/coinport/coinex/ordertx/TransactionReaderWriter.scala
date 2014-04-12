package com.coinport.coinex.ordertx

import akka.actor.{ Actor, ActorLogging }
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.common.PersistentId._
import Implicits._
import akka.persistence.Persistent

class TransactionReader(db: MongoDB) extends Actor with TransactionMongoHandler with ActorLogging {
  val coll = db("transaction")

  def receive = LoggingReceive {
    case q: QueryTransaction =>
      val xx = getItems(q)
      xx.foreach(println)
      sender ! QueryTransactionResult(getItems(q), countItems(q))
  }
}

class TransactionWriter(db: MongoDB) extends ExtendedView with TransactionMongoHandler with ActorLogging {
  val processorId = MARKET_UPDATE_PROCESSOR <<
  val coll = db("transaction")

  def receive = LoggingReceive {
    case e @ Persistent(OrderSubmitted(orderInfo, txs), _) =>
      txs foreach { t =>
        val ia = t.makerUpdate.previous.quantity - t.makerUpdate.current.quantity
        val oa = t.takerUpdate.previous.quantity - t.takerUpdate.current.quantity

        val price = t.makerUpdate.current.price.get

        val (taker, toId) = (t.takerUpdate.current.userId, t.takerUpdate.current.id)
        val (maker, moId) = (t.makerUpdate.current.userId, t.makerUpdate.current.id)

        val item = TransactionItem(tid = t.id, price = price, volume = ia, amount = oa, taker = taker,
          maker = maker, tOrder = toId, mOrder = moId, side = t.side, timestamp = t.timestamp)
        addItem(item)
      }
  }
}
