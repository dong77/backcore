/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.data.Implicits._

object AccountService extends AkkaService {
  def getAccount(uid: Long): Future[ApiResult] = {
    backend ? QueryAccount(uid) map {
      case result: QueryAccountResult =>
        val userAccount: com.coinport.coinex.api.model.UserAccount = result.userAccount
        ApiResult(true, 0, "", Some(userAccount))
    }
  }

  def deposit(uid: Long, currency: Currency, amount: Double): Future[ApiResult] = {
    val internalAmount: Long = amount.internalValue(currency)

    val deposit = Deposit(0L, uid.toLong, currency, internalAmount, TransferStatus.Pending)
    backend ? DoRequestCashDeposit(deposit) map {
      case result: RequestCashDepositSucceeded =>
        // TODO: confirm by admin dashboard
        backend ! AdminConfirmCashDepositSuccess(result.deposit)

        ApiResult(true, 0, "充值申请已提交", Some(result))
      case failed: RequestCashDepositFailed =>
        ApiResult(false, 1, "充值失败", Some(failed))
    }
  }

  def submitOrder(userOrder: UserOrder): Future[ApiResult] = {
    val command = userOrder.toDoSubmitOrder
    backend ? command map {
      case result: OrderSubmitted =>
        println("order submit -> ", result)
        ApiResult(true, 0, "订单提交成功", Some(UserOrder.fromOrderInfo(result.originOrderInfo)))
      case failed: SubmitOrderFailed =>
        val message = failed.error match {
          case ErrorCode.InsufficientFund => "余额不足"
          case error => "未知错误-" + error
        }
        ApiResult(false, failed.error.getValue, message)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def cancelOrder(id: Long, uid: Long): Future[ApiResult] = {
    println("cancel order: " + id)
    backend ? DoCancelOrder(Btc ~> Rmb, id, uid) map {
      case result: OrderCancelled => ApiResult(true, 0, "订单已撤销", Some(result.order))
      case x => ApiResult(false, -1, x.toString)
    }
  }

  def getOrders(uid: Option[Long], id: Option[Long], status: Option[OrderStatus], skip: Int, limit: Int): Future[ApiResult] = {
    val cursor = Cursor(skip, limit)
    // struct QueryOrder {1: optional i64 uid, 2: optional i64 oid, 3:optional i32 status, 4:optional MarketSide side, 5: Cursor cursor, 6: bool getCount}
    backend ? QueryOrder(uid, id, status.map(_.getValue), None, cursor, false) map {
      case result: QueryOrderResult =>
        val data = result.orderinfos.map {
          o => UserOrder.fromOrderInfo(o)
        }.toSeq
        ApiResult(data = Some(data))
      case x => ApiResult(false, -1, x.toString)
    }
  }
}
