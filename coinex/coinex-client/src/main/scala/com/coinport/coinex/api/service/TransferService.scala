package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import TransferType._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TransferService extends AkkaService {
  def countTransfers(): Future[ApiResult] = {
    null
  }

  def getTransfers(userId: Option[Long], currency: Option[Currency], status: Option[TransferStatus], spanCur: Option[SpanCursor], transferType: Option[TransferType], cur: Cursor): Future[ApiResult] = {
    backend ? QueryTransfer(userId, currency, status, spanCur, transferType, cur, false) map {
      case result: QueryTransferResult =>
        val items = result.transfers.map { d =>
          ApiTransferItem(d.id.toString, d.userId.toString,
            CurrencyObject(
              d.currency,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency),
              d.amount),
            d.status.value, d.created.getOrElse(0), d.updated.getOrElse(0), d.`type` == Deposit)
        }
        ApiResult(data = Some(items))
      case x => ApiResult(false)
    }
  }
}
