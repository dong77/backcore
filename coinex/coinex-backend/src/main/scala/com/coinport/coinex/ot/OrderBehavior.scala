package com.coinport.coinex.ot

import com.coinport.coinex.data._
import com.mongodb.casbah.MongoDB
import akka.actor.{ ActorLogging, Actor }
import akka.event.LoggingReceive
import Implicits._
import com.coinport.coinex.common.ExtendedView
import akka.persistence.Persistent

class OrderReader(db: MongoDB) extends Actor with OrderMongoHandler with ActorLogging {
  val coll = db("order")

  def receive = LoggingReceive {
    case DumpStateToFile => log.info("")

    case q: QueryOrder =>
      sender ! QueryOrderResult(getItems(q), countItems(q))
  }
}

class OrderWriter(db: MongoDB) extends Actor with OrderMongoHandler with ActorLogging {
  val coll = db("order")

  def receive = LoggingReceive {
    case DumpStateToFile => log.info("")

    case OrderCancelled(_, order) => cancelItem(order.id)

    case OrderSubmitted(orderInfo, txs) =>
      addItem(orderInfo)
      txs.foreach { tx =>
        val outAmount = tx.makerUpdate.current.inAmount
        val inAmount = tx.takerUpdate.previous.quantity - tx.takerUpdate.current.quantity
        val status =
          if (tx.makerUpdate.current.isFullyExecuted) OrderStatus.FullyExecuted
          else OrderStatus.PartiallyExecuted

        updateItem(tx.makerUpdate.current.id, inAmount, outAmount, status.getValue(), orderInfo.side.reverse, tx.timestamp)
      }
  }
}

