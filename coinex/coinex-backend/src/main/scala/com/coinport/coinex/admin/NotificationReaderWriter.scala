package com.coinport.coinex.admin

import com.mongodb.casbah.Imports._
import akka.actor.{ ActorLogging, Actor }
import akka.event.LoggingReceive
import com.coinport.coinex.data.{ SetNotification, QueryNotificationsResult, QueryNotification }

class NotificationReaderWriter(val db: MongoDB) extends Actor with NotificationHandler with ActorLogging {

  def receive = LoggingReceive {
    case n: SetNotification =>
      val notification = if (n.notification.id <= 0) n.notification else n.notification.copy(id = idGenerator)
      notificationHandler.put(notification)
    case q: QueryNotification =>
      val query = notificationHandler.getQueryDBObject(q)
      val items = notificationHandler.find(query, q.cur.skip, q.cur.limit)
      val count = notificationHandler.count(query)
      sender ! QueryNotificationsResult(items, count)
  }
}
