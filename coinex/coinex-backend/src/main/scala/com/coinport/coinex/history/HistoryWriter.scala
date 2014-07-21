package com.coinport.coinex.history

import akka.actor.Actor
import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import org.json4s.native.Serialization.{ read, write }

class HistoryWriter(db: MongoDB) extends Actor {
  val userActions = new SimpleJsonMongoCollection[UserAction, UserAction.Immutable] {
    val coll = db("user_actions")
    def extractId(userAction: UserAction) = userAction.id

    def find(skip: Int, limit: Int): Seq[UserAction] =
      try {
        coll.find().sort(MongoDBObject(ID -> -1)).skip(skip).limit(limit).map { json => read[UserAction.Immutable](json.get(DATA).toString) }.toSeq
      } catch {
        case e: Throwable => Seq.empty[UserAction]
      }
  }

  var lastUserActionId = loadLastIdFromDB

  def loadLastIdFromDB = {
    val lastUA = userActions.find(0, 2)
    if (lastUA.nonEmpty) lastUA.head.id else 0L
  }

  def receive = {
    case PersistUserAction(userAction) =>
      val userActionWithId = userAction.copy(id = lastUserActionId + 1)
      lastUserActionId = userAction.id
      userActions.put(userActionWithId)
  }
}
