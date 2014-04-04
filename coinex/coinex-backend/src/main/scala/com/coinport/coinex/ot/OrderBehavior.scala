package com.coinport.coinex.ot

import com.coinport.coinex.data._
import com.mongodb.casbah.MongoDB
import akka.actor.{ ActorLogging, Actor }
import akka.event.LoggingReceive
import Implicits._

class OrderReader(db: MongoDB) extends Actor with OrderMongoHandler with ActorLogging {
  val coll = db("order")

  def receive = LoggingReceive {
    case DebugDump => log.info("")

    case q: QueryOrder =>
      sender ! QueryOrderResult(getItems(q), countItems(q))
  }
}

class OrderWriter(db: MongoDB) extends Actor with OrderMongoHandler with ActorLogging {
  val coll = db("order")

  def receive = LoggingReceive {
    case DebugDump => log.info("")

    case OrderCancelled(_, order) => cancelItem(order.id)

    case m: OrderSubmitted =>
      addItem(m.originOrderInfo)
      m.txs.foreach { tx =>
        val outAmount = tx.makerUpdate.current.inAmount
        val inAmount = tx.takerUpdate.previous.quantity - tx.takerUpdate.current.quantity
        val status =
          if (tx.makerUpdate.current.isFullyExecuted) OrderStatus.FullyExecuted
          else OrderStatus.PartiallyExecuted

        updateItem(tx.makerUpdate.current.id, inAmount, outAmount, status.getValue(), m.originOrderInfo.side.reverse, tx.timestamp)
      }
  }
}

