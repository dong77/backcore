package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

object OpenService extends AkkaService {
//  /* C    */ struct QueryReserveStatus                      {1: _Currency currency}
//  /* R    */ struct QueryReserveStatusResult                {1: _Currency currency, 2: map<_CryptoCurrencyAddressType, i64> amounts}

  def getCurrencyReserve(currency: Currency) = {
    backend ? QueryReserveStatus(currency) map {
      case rv: QueryReserveStatusResult =>
        ApiResult(data = Some(rv.amounts.map(a => a._1.toString -> CurrencyObject(currency, a._2))))
    }
  }
}
