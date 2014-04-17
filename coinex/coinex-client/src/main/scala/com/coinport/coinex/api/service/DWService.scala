package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DWService extends AkkaService {
  def countDeposis(): Future[ApiResult] = {
    null
  }

  def getDeposits(userId: Option[Long], currency: Option[Currency], status: Option[TransferStatus], spanCur: Option[SpanCursor], cur: Cursor): Future[ApiResult] = {
    backend ? QueryDeposit(userId, currency, status, spanCur, cur, false) map {
      case result: QueryDepositResult =>
        val items = result.deposits.map { d =>
          ApiDeposit(d.id.toString, d.userId.toString,
            CurrencyObject(
              d.currency,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency),
              d.amount),
            d.status.value, d.created.getOrElse(0), d.updated.getOrElse(0))
        }
        ApiResult(data = Some(items))
      case x => ApiResult(false)
    }
  }

  def countWithdrawal(userId: Long, currency: Option[Currency], status: Option[TransferStatus], spanCur: SpanCursor, cur: Cursor): Future[ApiResult] = {
    null
  }

  def getWithdrawal(userId: Option[Long], currency: Option[Currency], status: Option[TransferStatus], spanCur: Option[SpanCursor], cur: Cursor): Future[ApiResult] = {
    backend ? QueryWithdrawal(userId, currency, status, spanCur, cur, false) map {
      case result: QueryWithdrawalResult =>
        val items = result.withdrawals.map { d =>
          ApiWithdrawal(d.id.toString, d.userId.toString,
            CurrencyObject(
              d.currency,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency),
              d.amount),
            d.status.value, d.created.getOrElse(0), d.updated.getOrElse(0))
        }
        ApiResult(data = Some(items))
      case x => ApiResult(false)
    }
  }
}
