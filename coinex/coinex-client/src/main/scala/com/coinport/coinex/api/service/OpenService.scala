package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

object OpenService extends AkkaService {
  def getCurrencyReserve(currency: Currency) = {
    backend ? QueryReserveStatus(currency) map {
      case rv: QueryReserveStatusResult =>
        val amount = rv._2.map(_._2).sum
        ApiResult(data = Some(ApiCurrencyReserve(CurrencyObject(currency, amount))))
      case r =>
        ApiResult(false, -1, "unknown result", Some(r))
    }
  }
}
