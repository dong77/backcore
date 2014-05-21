package com.coinport.coinex.admin

import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.data.{ QueryNotification, Notification }
import com.mongodb.casbah.Imports._

trait NotificationHandler {
  val db: MongoDB
  val notificationHandler = new SimpleJsonMongoCollection[Notification, Notification.Immutable]() {
    lazy val coll = db("notifications")

    def extractId(n: Notification) = n.id

    def getQueryDBObject(q: QueryNotification): MongoDBObject = {
      var query = MongoDBObject()
      if (q.author.isDefined) query ++= MongoDBObject(DATA + "." + Notification.AuthorField.name -> q.author.get)
      if (q.id.isDefined) query ++= MongoDBObject(DATA + "." + Notification.IdField.name -> q.id.get)
      if (q.ntype.isDefined) query ++= MongoDBObject(DATA + "." + Notification.NTypeField.name -> q.ntype.get)
      if (q.getRemoved.isDefined) query ++= MongoDBObject(DATA + "." + Notification.NTypeField.name -> q.getRemoved.get)
      query
    }
  }

  def idGenerator = notificationHandler.count(null) + 1
}
