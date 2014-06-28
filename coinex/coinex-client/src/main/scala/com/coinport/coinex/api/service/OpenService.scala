package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.data.CryptoCurrencyAddressType._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

object OpenService extends AkkaService {
  def getCurrencyReserve(currency: Currency) = {
    backend ? QueryReserveStatus(currency) map {
      case rv: QueryReserveStatusResult =>
        val total = rv._2.map(_._2).sum.toLong
        val user = rv.amounts.get(CryptoCurrencyAddressType.User).getOrElse(0L)
        val hot = rv.amounts.get(Hot).getOrElse(0L)
        val cold = rv.amounts.get(Cold).getOrElse(0L)
        val available = hot + cold
        ApiResult(data = Some(ApiCurrencyReserve(
          CurrencyObject(currency, available),
          CurrencyObject(currency, total),
          CurrencyObject(currency, user),
          CurrencyObject(currency, hot),
          CurrencyObject(currency, cold)
        )))
      case r =>
        ApiResult(false, -1, "unknown result", Some(r))
    }
  }
}
