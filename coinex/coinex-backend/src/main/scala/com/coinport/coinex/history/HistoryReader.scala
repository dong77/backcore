package com.coinport.coinex.history

import akka.actor.{ ActorLogging, Actor }
import com.mongodb.casbah.Imports._
import com.coinport.coinex.data._
import com.coinport.coinex.common.ExtendedActor
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection

class HistoryReader(db: MongoDB) extends ExtendedActor with ActorLogging {
  val LimitMax = 100

  def receive = {
    case QueryUserAction(userId, actionType) =>
      val items = userActions.findActionByUserId(userId, actionType)
      sender ! QueryUserActionResult(items, items.size)
  }

  val userActions = new SimpleJsonMongoCollection[UserAction, UserAction.Immutable] {
    val coll = db("user_actions")
    def extractId(userAction: UserAction) = userAction.id
    def findActionByUserId(userId: Long, actionType: UserActionType): Seq[UserAction] = {
      val cond = MongoDBObject("data.userId" -> userId, "data.actionType" -> actionType.toString)
      find(cond, 0, LimitMax)
    }
  }

}
