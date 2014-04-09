/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.mongo

import org.specs2.mutable._
import com.coinport.coinex.dw.DepositWithdrawBehavior
import com.coinport.coinex.data._
import com.mongodb.casbah._
import scala.Some
import com.coinport.coinex.common.EmbeddedMongoSupport

class DepositWithdrawSpec extends Specification with EmbeddedMongoSupport {
  step(embeddedMongoStartup())

  class DWClass(val db: MongoDB) extends DepositWithdrawBehavior

  "DepositWithdrawSpec" should {
    val dw = new DWClass(database)
    "can save deposits and query them" in {
      val d1 = Deposit(id = 1, userId = 1, currency = Currency.Rmb, amount = 1000, created = Some(100), updated = Some(800))
      val d2 = Deposit(id = 2, userId = 1, currency = Currency.Btc, amount = 2000, created = Some(200), updated = Some(800))
      val d3 = Deposit(id = 3, userId = 2, currency = Currency.Rmb, amount = 1000, created = Some(300), updated = Some(800))
      val d4 = Deposit(id = 4, userId = 2, currency = Currency.Btc, amount = 2000, created = Some(400), updated = Some(800))
      val seq = Seq(d1, d2, d3, d4)
      seq.foreach(d => dw.deposits.put(d))

      var q = QueryDeposit(cur = Cursor(0, 10), getCount = true)
      dw.deposits.count(dw.deposits.getQueryDBObject(q)) mustEqual 4

      q = QueryDeposit(uid = Some(1), cur = Cursor(0, 10), getCount = true)
      dw.deposits.count(dw.deposits.getQueryDBObject(q)) mustEqual 2
      q = QueryDeposit(uid = Some(1), cur = Cursor(0, 10), getCount = false)
      dw.deposits.find(dw.deposits.getQueryDBObject(q), 0, 10).map(_.id) mustEqual Seq(2, 1)

      q = QueryDeposit(uid = Some(1), currency = Some(Currency.Rmb), cur = Cursor(0, 10), getCount = true)
      dw.deposits.count(dw.deposits.getQueryDBObject(q)) mustEqual 1
      q = QueryDeposit(uid = Some(1), currency = Some(Currency.Rmb), cur = Cursor(0, 10), getCount = false)
      dw.deposits.find(dw.deposits.getQueryDBObject(q), 0, 10).map(_.id) mustEqual Seq(1)

      q = QueryDeposit(spanCur = Some(SpanCursor(300, 200)), cur = Cursor(0, 10), getCount = true)
      dw.deposits.count(dw.deposits.getQueryDBObject(q)) mustEqual 2
      q = QueryDeposit(spanCur = Some(SpanCursor(300, 200)), cur = Cursor(0, 10), getCount = true)
      dw.deposits.find(dw.deposits.getQueryDBObject(q), 0, 10).map(_.id) mustEqual Seq(3, 2)
    }
  }

  step(embeddedMongoShutdown())
}
