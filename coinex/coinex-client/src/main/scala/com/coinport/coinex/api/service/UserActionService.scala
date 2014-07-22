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

object UserActionService extends AkkaService {
  override def hashCode(): Int = super.hashCode()

  def getLoginHistory(userId: Long) = {
    val command = QueryUserAction(userId, UserActionType.Login)
    backend ? command map {
      case result: QueryUserActionResult =>
        ApiResult(true, 0, "", Some(result.userActions.map(thrift2pojo)))
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  private def thrift2pojo(t: UserAction) =
    new UserActionPojo(t.id, t.userId, t.timestamp, t.actionType, t.ipAddress, t.location)

}
