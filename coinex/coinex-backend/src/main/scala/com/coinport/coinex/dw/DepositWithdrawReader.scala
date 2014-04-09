package com.coinport.coinex.dw

import akka.actor.{ ActorLogging, Actor }
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._

class DepositWithdrawReader(val db: MongoDB) extends Actor with DepositWithdrawBehavior with ActorLogging {
  def receive = LoggingReceive {
    case DebugDump => log.info("DepositWithdrawReader")

    case q: QueryDeposit =>
      val query = getQueryDBObject(q)
      val count = if (q.getCount) deposits.count(query) else 0
      val items = if (!q.getCount) deposits.find(query, q.cur.skip, q.cur.limit) else Nil
      sender ! QueryDepositResult(items, count)

    case q: QueryWithdrawal =>
      val query = getQueryDBObject(q)
      val count = if (q.getCount) withdrawals.count(query) else 0
      val items = if (!q.getCount) withdrawals.find(query, q.cur.skip, q.cur.limit) else Nil
      sender ! QueryWithdrawalResult(items, count)
  }

  private def getQueryDBObject(q: QueryDeposit): MongoDBObject = {
    var query = MongoDBObject()
    if (q.uid.isDefined) query ++= MongoDBObject(deposits.DATA + "." + Deposit.UserIdField.name -> q.uid.get)
    if (q.currency.isDefined) query ++= MongoDBObject(deposits.DATA + "." + Deposit.CurrencyField.name -> q.currency.get)
    if (q.status.isDefined) query ++= MongoDBObject(deposits.DATA + "." + Deposit.StatusField.name -> q.status.get)
    if (q.spanCur.isDefined) query ++= (deposits.DATA + "." + Deposit.StatusField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
    query
  }

  private def getQueryDBObject(q: QueryWithdrawal): MongoDBObject = {
    var query = MongoDBObject()
    if (q.uid.isDefined) query ++= MongoDBObject(withdrawals.DATA + "." + Withdrawal.UserIdField.name -> q.uid.get)
    if (q.currency.isDefined) query ++= MongoDBObject(withdrawals.DATA + "." + Withdrawal.CurrencyField.name -> q.currency.get)
    if (q.status.isDefined) query ++= MongoDBObject(withdrawals.DATA + "." + Withdrawal.StatusField.name -> q.status.get)
    if (q.spanCur.isDefined) query ++= (withdrawals.DATA + "." + Withdrawal.StatusField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
    query
  }
}