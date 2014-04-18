package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DWService extends AkkaService {
  def countDW(): Future[ApiResult] = {
    null
  }

  def getDWItems(userId: Option[Long], currency: Option[Currency], status: Option[TransferStatus], spanCur: Option[SpanCursor], isDeposit: Option[Boolean], cur: Cursor): Future[ApiResult] = {
    backend ? QueryDW(userId, currency, status, spanCur, isDeposit, cur, false) map {
      case result: QueryDWResult =>
        val items = result.dwitems.map { d =>
          ApiDWItem(d.id.toString, d.userId.toString,
            CurrencyObject(
              d.currency,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency).toString,
              d.amount.externalValue(d.currency),
              d.amount),
            d.status.value, d.created.getOrElse(0), d.updated.getOrElse(0), d.isDeposit)
        }
        ApiResult(data = Some(items))
      case x => ApiResult(false)
    }
  }
}
