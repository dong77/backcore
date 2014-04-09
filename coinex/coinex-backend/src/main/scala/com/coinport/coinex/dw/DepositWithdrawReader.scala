package com.coinport.coinex.dw

import akka.actor.{ ActorLogging, Actor }
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._

class DepositWithdrawReader(val db: MongoDB) extends Actor with DepositWithdrawBehavior with ActorLogging {
  def receive = LoggingReceive {
    case DebugDump => log.info("DepositWithdrawReader")

    case q: QueryDeposit =>
      val query = deposits.getQueryDBObject(q)
      val count = if (q.getCount) deposits.count(query) else 0
      val items = if (!q.getCount) deposits.find(query, q.cur.skip, q.cur.limit) else Nil
      sender ! QueryDepositResult(items, count)

    case q: QueryWithdrawal =>
      val query = withdrawals.getQueryDBObject(q)
      val count = if (q.getCount) withdrawals.count(query) else 0
      val items = if (!q.getCount) withdrawals.find(query, q.cur.skip, q.cur.limit) else Nil
      sender ! QueryWithdrawalResult(items, count)
  }
}