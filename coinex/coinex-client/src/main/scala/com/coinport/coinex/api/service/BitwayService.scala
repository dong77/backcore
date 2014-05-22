package com.coinport.coinex.api.service

import com.coinport.coinex.api.model._
import com.coinport.coinex.data._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

object BitwayService extends AkkaService {
  def getNetworkStatus(currency: Currency) = {
    backend ? QueryCryptoCurrencyNetworkStatus(currency) map {
      case result: QueryCryptoCurrencyNetworkStatusResult =>
        val data = ApiNetworkStatus(
          currency = result.currency,
          timestamp = result.status.heartbeatTime.getOrElse(0L),
          height = result.status.height,
          block = result.status.id
        )
        ApiResult(data = Some(data))
      case r =>
        ApiResult(false, -1, "unknown result", Some(r))
    }
  }
}
