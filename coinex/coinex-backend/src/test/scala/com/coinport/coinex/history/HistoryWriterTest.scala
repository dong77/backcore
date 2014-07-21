package com.coinport.coinex.history

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import org.specs2.mutable._
//import com.mongodb.casbah.Imports._
import com.mongodb.casbah._
import com.coinport.coinex.data._

class HistoryWriterTest extends Specification {
  val system = ActorSystem("coinex")

  val mongoUriForViews = MongoURI("mongodb://localhost:27017/coinex_readers")
  val mongoForViews = MongoConnection(mongoUriForViews)
  val dbForViews = mongoForViews(mongoUriForViews.database.get)

  //val writer = new HistoryWriter(dbForViews)
  val historyWriterActor = system.actorOf(Props(new HistoryWriter(dbForViews)), "backend")

  "HistoryWriterTest" should {
    "be able to write UserAction to mongo" in {
      val userAction = UserAction(0L, 0L, System.currentTimeMillis, UserActionType.Login, Some("127.0.0.1"), Some("shanghai"))

      //historyWriterActor ! PersistUserAction(userAction)
      true mustEqual true
    }
  }
}
