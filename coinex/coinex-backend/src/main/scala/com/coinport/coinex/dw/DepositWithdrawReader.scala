package com.coinport.coinex.dw

import akka.actor.{ ActorLogging, Actor }
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._

class DepositWithdrawReader(val db: MongoDB) extends Actor with DepositWithdrawBehavior with ActorLogging {
  def receive = LoggingReceive {
    case q: QueryDW =>
      val query = dwHandler.getQueryDBObject(q)
      val count = if (q.getCount) dwHandler.count(query) else 0
      val items = if (!q.getCount) dwHandler.find(query, q.cur.skip, q.cur.limit) else Nil
      sender ! QueryDWResult(items, count)
  }
}