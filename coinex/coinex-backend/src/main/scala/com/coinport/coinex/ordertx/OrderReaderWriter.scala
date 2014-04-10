package com.coinport.coinex.ordertx

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
    case q: QueryOrder =>
      val xx = getItems(q)
      xx.foreach(println)
      sender ! QueryOrderResult(getItems(q), countItems(q))
  }
}

class OrderWriter(db: MongoDB) extends Actor with OrderMongoHandler with ActorLogging {
  val coll = db("order")

  def receive = LoggingReceive {
    case OrderCancelled(_, order) => cancelItem(order.id)

    case OrderSubmitted(orderInfo, txs) =>
      var takerQuantity = 0L
      txs.foreach { tx =>
        takerQuantity = tx.takerUpdate.current.quantity
        val quantity = tx.makerUpdate.current.quantity
        val inAmount = tx.makerUpdate.current.inAmount
        val status =
          if (tx.makerUpdate.current.isFullyExecuted) OrderStatus.FullyExecuted
          else OrderStatus.PartiallyExecuted

        updateItem(tx.makerUpdate.current.id, inAmount, quantity, status.getValue(), orderInfo.side.reverse, tx.timestamp)
      }
      addItem(orderInfo, takerQuantity)
  }
}

