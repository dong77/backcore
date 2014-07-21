/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.service

import com.coinport.coinex.api.model._
import com.coinport.coinex.data._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await.result
import scala.concurrent.Await

object HistoryService extends AkkaService {

  def saveUserAction(userAction: UserAction) = {
    val command = PersistUserAction(userAction)
    backend ? command
    ApiResult(true, 0, "")
  }
}
