package com.coinport.coinex.api.service

import com.coinport.coinex.data._
import com.coinport.coinex.api.model._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object NotificationService extends AkkaService {
  // TODO: store notifications in actors
  val notifications = collection.mutable.HashMap[Long, Notification]()

  def getNotifications() = {
    ApiResult(data = Some(notifications.values.toSeq))
  }

  def addNotification(notification: Notification) = {
    notifications.put(notification.id, notification)
  }

  def removeNotification(id: Long) = {
    notifications.remove(id)
  }
}
