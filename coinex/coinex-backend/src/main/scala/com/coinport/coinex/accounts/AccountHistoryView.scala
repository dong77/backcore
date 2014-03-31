/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */
package com.coinport.coinex.accounts

import com.coinport.coinex.common._
import com.coinport.coinex.data._
import akka.event.LoggingReceive
import akka.persistence.Persistent

class AccountHistoryView(side: MarketSide) extends ExtendedView {
  override val processorId = "coinex_ap"
  override val viewId = "coinex_history_view"
  val manager = new AccountManager()

  def receive = LoggingReceive {
    case DebugDump =>
      log.info("state: {}", manager())

    //    case Persistent(DoDepositCash(userId, currency, amount), _) =>
    //      manager.updateCashAccount(userId, CashAccount(currency, amount, 0, 0))
    //
    //    case Persistent(DoConfirmCashWithdrawalSuccess(userId, currency, amount), _) =>
    //      manager.updateCashAccount(userId, CashAccount(currency, -amount, 0, 0))
    //
    //    case Persistent(m: OrderSubmitted, _) =>
    //      m.txs foreach { tx =>
    //        val Transaction(_, takerOrderUpdate, makerOrderUpdate) = tx
    //        manager.sendCash(takerOrderUpdate.userId, makerOrderUpdate.userId, side.outCurrency, takerOrderUpdate.outAmount)
    //        manager.sendCash(makerOrderUpdate.userId, takerOrderUpdate.userId, side.inCurrency, makerOrderUpdate.outAmount)
    //        manager.conditionalRefund(takerOrderUpdate.current.hitTakeLimit)(side.outCurrency, takerOrderUpdate.current)
    //        manager.conditionalRefund(makerOrderUpdate.current.hitTakeLimit)(side.inCurrency, makerOrderUpdate.current)
    //      }
    //
    //    case QueryAccount(userId) =>
    //      sender ! QueryAccountResult(manager().getUserAccounts(userId))
  }
}

class AccountHistoryManager extends Manager[AccountHistoryState](AccountHistoryState()) {

  //  def sendCash(from: Long, to: Long, currency: Currency, amount: Long) = {
  //    updateCashAccount(from, CashAccount(currency, 0, -amount, 0))
  //    updateCashAccount(to, CashAccount(currency, amount, 0, 0))
  //  }
  //
  //  def conditionalRefund(condition: Boolean)(currency: Currency, order: Order) = {
  //    if (condition && order.quantity > 0) refund(order.userId, currency, order.quantity)
  //  }
  //
  //  def refund(uid: Long, currency: Currency, quantity: Long): AccountOperationResult = {
  //    updateCashAccount(uid, CashAccount(currency, quantity, -quantity, 0))
  //  }
  //
  //  def updateCashAccount(userId: Long, adjustment: CashAccount) = {
  //    val current = state.getUserCashAccount(userId, adjustment.currency)
  //    val updated = current + adjustment
  //
  //    if (updated.isValid) {
  //      state = state.setUserCashAccount(userId, updated)
  //      AccountOperationResult(Ok, updated)
  //    } else {
  //      AccountOperationResult(InsufficientFund, current)
  //    }
  //  }
  //
  //  def getAndIncreaseOrderId(): Long = {
  //    state = state.increaselLastOrderId()
  //    state.lastOrderId
  //  }
}
