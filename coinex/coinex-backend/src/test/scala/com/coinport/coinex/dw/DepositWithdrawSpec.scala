/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.dw

import com.coinport.coinex.data._
import com.mongodb.casbah._
import com.coinport.coinex.common.EmbeddedMongoForTestWithBF
import com.coinport.coinex.data.Implicits._

class DepositWithdrawSpec extends EmbeddedMongoForTestWithBF {

  "DepositWithdrawSpec" should {
    val dw = new DepositWithdrawBehavior { val db = database }
    "be able to save dwHandler and query them" in {
      val d1 = Deposit(id = 1, userId = 1, currency = Currency.Rmb, amount = 1000, created = Some(100), updated = Some(800))
      val d2 = Deposit(id = 2, userId = 1, currency = Currency.Btc, amount = 2000, created = Some(200), updated = Some(800))
      val d3 = Deposit(id = 3, userId = 2, currency = Currency.Rmb, amount = 1000, created = Some(300), updated = Some(800))
      val d4 = Deposit(id = 4, userId = 2, currency = Currency.Btc, amount = 2000, created = Some(400), updated = Some(800))
      val seq = Seq(d1, d2, d3, d4)
      seq.foreach(d => dw.dwHandler.put(d))

      var q = QueryDW(cur = Cursor(0, 10), getCount = true)
      dw.dwHandler.count(dw.dwHandler.getQueryDBObject(q)) should be(4)

      q = QueryDW(uid = Some(1), cur = Cursor(0, 10), getCount = true)
      dw.dwHandler.count(dw.dwHandler.getQueryDBObject(q)) should be(2)
      q = QueryDW(uid = Some(1), cur = Cursor(0, 10), getCount = false)
      dw.dwHandler.find(dw.dwHandler.getQueryDBObject(q), 0, 10).map(_.id) should equal(Seq(2, 1))

      q = QueryDW(uid = Some(1), currency = Some(Currency.Rmb), cur = Cursor(0, 10), getCount = true)
      dw.dwHandler.count(dw.dwHandler.getQueryDBObject(q)) should be(1)
      q = QueryDW(uid = Some(1), currency = Some(Currency.Rmb), cur = Cursor(0, 10), getCount = false)
      dw.dwHandler.find(dw.dwHandler.getQueryDBObject(q), 0, 10).map(_.id) should equal(Seq(1))

      q = QueryDW(spanCur = Some(SpanCursor(300, 200)), cur = Cursor(0, 10), getCount = true)
      dw.dwHandler.count(dw.dwHandler.getQueryDBObject(q)) should be(2)
      q = QueryDW(spanCur = Some(SpanCursor(300, 200)), cur = Cursor(0, 10), getCount = true)
      dw.dwHandler.find(dw.dwHandler.getQueryDBObject(q), 0, 10).map(_.id) should equal(Seq(3, 2))
    }
  }

}
