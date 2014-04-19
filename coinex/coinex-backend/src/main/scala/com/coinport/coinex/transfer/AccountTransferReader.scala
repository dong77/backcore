package com.coinport.coinex.transfer

import akka.actor.{ ActorLogging, Actor }
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._

class AccountTransferReader(val db: MongoDB) extends Actor with TransferBehavior with ActorLogging {
  val manager = new AccountTransferManager()

  def receive = LoggingReceive {
    case q: QueryTransfer =>
      val query = transferHandler.getQueryDBObject(q)
      val count = if (q.getCount) transferHandler.count(query) else 0
      val items = if (!q.getCount) transferHandler.find(query, q.cur.skip, q.cur.limit) else Nil
      sender ! QueryTransferResult(items, count)
  }
}