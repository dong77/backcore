/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.transfer

import com.coinport.coinex.data._
import com.coinport.coinex.common.EmbeddedMongoForTestWithBF
import com.coinport.coinex.integration.IntegrationSpec
import com.coinport.coinex.integration.Environment
import akka.event.LoggingAdapter
import scala.Some
import akka.event.Logging

import Currency._
import TransferType._
import TransferStatus._

class TransferBehaviorSpec extends IntegrationSpec(new Environment) {
  "AccountTransferWithdrawSpec" should {
    val dw = new AccountTransferBehavior {
      implicit val logger: LoggingAdapter = akka.event.Logging(env.system, this.getClass)
      //Cann't log anything
      val db = env.database
      val manager = new AccountTransferManager
    }

    var itemId = 6E12.toLong

    def resListIsEmpty(currency: Currency) {
      dw.batchAccountMessage(currency).size should equal(0)
      dw.batchBitwayMessage(currency).size should equal(0)
    }
    val accountTransferConfig = new AccountTransferConfig(false, Map.empty, Set.empty, Map.empty)
    dw.setSucceededRetainNum(accountTransferConfig.confirmNumMap)
    dw.intTransferHandlerObjectMap()
    val transferConfig = TransferConfig(manualCurrency = Some(accountTransferConfig.manualCurrency), enableAutoConfirm = Some(accountTransferConfig.enableAutoConfirm))

    "be able to save transferHandler and query them" in {
      val d1 = AccountTransfer(id = 1, userId = 1, `type` = TransferType.Deposit, currency = Currency.Cny, amount = 1000, created = Some(100), updated = Some(800))
      val d2 = AccountTransfer(id = 2, userId = 1, `type` = TransferType.Deposit, currency = Currency.Btc, amount = 2000, created = Some(200), updated = Some(800))
      val d3 = AccountTransfer(id = 3, userId = 2, `type` = TransferType.Deposit, currency = Currency.Cny, amount = 1000, created = Some(300), updated = Some(800))
      val d4 = AccountTransfer(id = 4, userId = 2, `type` = TransferType.Deposit, currency = Currency.Btc, amount = 2000, created = Some(400), updated = Some(800))
      val seq = Seq(d1, d2, d3, d4)
      seq.foreach(d => dw.transferHandler.put(d))

      var q = QueryTransfer(cur = Cursor(0, 10))
      dw.transferHandler.count(dw.transferHandler.getQueryDBObject(q)) should be(4)

      q = QueryTransfer(uid = Some(1), cur = Cursor(0, 10))
      dw.transferHandler.count(dw.transferHandler.getQueryDBObject(q)) should be(2)

      q = QueryTransfer(uid = Some(1), cur = Cursor(0, 10))
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q), 0, 10).map(_.id) should equal(Seq(2, 1))

      q = QueryTransfer(uid = Some(1), currency = Some(Currency.Cny), cur = Cursor(0, 10))
      dw.transferHandler.count(dw.transferHandler.getQueryDBObject(q)) should be(1)

      q = QueryTransfer(uid = Some(1), currency = Some(Currency.Cny), cur = Cursor(0, 10))
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q), 0, 10).map(_.id) should equal(Seq(1))

      q = QueryTransfer(spanCur = Some(SpanCursor(300, 200)), cur = Cursor(0, 10))
      dw.transferHandler.count(dw.transferHandler.getQueryDBObject(q)) should be(2)

      q = QueryTransfer(spanCur = Some(SpanCursor(300, 200)), cur = Cursor(0, 10))
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q), 0, 10).map(_.id) should equal(Seq(3, 2))
    }

    "check isCryptoCurrency as expected" in {
      dw.isTransferByBitway(AccountTransfer(0, 0, TransferType.DepositHot, Btc, 1), None) should equal(true)
    }

    "DoRequestTransfer act as expected" in {

      val q = QueryTransfer(uid = Some(1000000000), currency = Some(Btc), cur = Cursor(0, 10))

      val deposit = DoRequestTransfer(AccountTransfer(101, 1000000000, Deposit, Btc, 100, created = Some(System.currentTimeMillis())), None, Some(transferConfig))
      dw.updateState(deposit)
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q.copy(types = List(Deposit))), 0, 10).size should equal(0)
      dw.transferHandlerObjectMap(Deposit).id2HandlerMap.isEmpty should be(true)
      resListIsEmpty(Btc)

      val userToHot = DoRequestTransfer(AccountTransfer(102, 1000000000, UserToHot, Btc, 10, address = Some("userAddress"), created = Some(System.currentTimeMillis())), None, Some(transferConfig))
      dw.updateState(userToHot)
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q.copy(types = List(UserToHot))), 0, 10).size should equal(0)
      dw.transferHandlerObjectMap(UserToHot).id2HandlerMap.isEmpty should be(true)
      resListIsEmpty(Btc)

      val withdrawal = DoRequestTransfer(AccountTransfer(103, 1000000000, Withdrawal, Btc, 10, created = Some(System.currentTimeMillis())), None, Some(transferConfig))
      dw.updateState(withdrawal)
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q.copy(types = List(Withdrawal))), 0, 10).map(_.id) should equal(Seq(103))
      dw.transferHandlerObjectMap(Withdrawal).id2HandlerMap.isEmpty should be(true)
      resListIsEmpty(Btc)

      val coldToHot = DoRequestTransfer(AccountTransfer(104, 1000000000, ColdToHot, Btc, 10, created = Some(System.currentTimeMillis())), None, Some(transferConfig))
      dw.updateState(coldToHot)
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q.copy(types = List(ColdToHot))), 0, 10).map(_.id) should equal(Seq(104))
      dw.transferHandlerObjectMap(ColdToHot).id2HandlerMap.isEmpty should be(true)
      resListIsEmpty(Btc)

      val hotToCold = DoRequestTransfer(AccountTransfer(105, 1000000000, HotToCold, Btc, 10, created = Some(System.currentTimeMillis())), None, Some(transferConfig))
      dw.updateState(hotToCold)
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q.copy(types = List(HotToCold))), 0, 10).map(_.id) should equal(Seq(105))
      dw.transferHandlerObjectMap(HotToCold).id2HandlerMap.size should be(1)
      dw.batchAccountMessage(Btc).values.size should equal(0)
      dw.batchBitwayMessage(Btc).values.size should equal(1)
      val h2CItem = dw.transferHandlerObjectMap(HotToCold).id2HandlerMap.values.head.item
      itemId += 1
      h2CItem.id should equal(itemId)
      h2CItem.txType.get should equal(HotToCold)

      val unknown = DoRequestTransfer(AccountTransfer(106, 1000000000, TransferType.Unknown, Btc, 10, created = Some(System.currentTimeMillis())), None, Some(transferConfig))
      dw.updateState(unknown)
      dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q.copy(types = List(TransferType.Unknown))), 0, 10).map(_.id) should equal(Seq(106))

    }

    "admin confirm success act as expected" in {
      val q = QueryTransfer(uid = Some(2000000000), currency = Some(Btc), cur = Cursor(0, 10))
      val success = AdminConfirmTransferSuccess(AccountTransfer(107, 2000000000, Withdrawal, Btc, 10, address = Some("userAddress")))
      dw.updateState(success)
      val withdrawal = dw.transferHandler.find(dw.transferHandler.getQueryDBObject(q.copy(types = List(Withdrawal))), 0, 10)
      withdrawal.size should equal(1)

    }
  }
}
