package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object NotificationService extends AkkaService {
  // TODO: ask admin for notifications
  def getNotifications() = {
    val notifications = List(Notification(NotificationType.Warning, "Welcome to Coinport"))
    ApiResult(data = Some(notifications))
  }
}
