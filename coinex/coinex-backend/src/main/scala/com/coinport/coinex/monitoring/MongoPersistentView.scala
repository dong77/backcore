package com.coinport.coinex.monitoring

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView
import akka.persistence._
import com.coinport.coinex.data._
import com.coinport.coinex.util.Hash
import com.coinport.coinex.serializers.ThriftJsonSerializer
import Implicits._
import com.mongodb.util.JSON
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.{ MongoURI }

class MongoPersistentView(mongoUri: String, pid: String) extends ExtendedView {
  override val processorId = pid
  override val viewId = pid + "_mongop"

  val serializer = new ThriftJsonSerializer
  val uri = MongoURI(mongoUri)
  val mongo = MongoConnection(uri)
  val database = mongo(uri.database.getOrElse("coinex_export"))
  val collection = database(pid + "_events")

  case class State(index: Long, hash: String)

  var state = State(0, "0" * 32)

  def receive = LoggingReceive {
    case TakeSnapshotNow => {
      saveSnapshot(state)
      postSnapshot(state)
    }
    case SnapshotOffer(meta, snapshot) => state =
      snapshot.asInstanceOf[State]

    case Persistent(m: AnyRef, _) =>
      val data = new String(serializer.toBinary(m))
      val event = m.getClass.getName.replace("com.coinport.coinex.data.", "").replace("$Immutable", "")

      val builder = MongoDBObject.newBuilder
      builder += "_id" -> state.index
      builder += event -> JSON.parse(data)
      builder += "prehash" -> state.hash

      val hash = Hash.sha1Base32(builder.result.toString)
      builder += "hash" -> hash

      collection += builder.result
      state = State(state.index + 1, hash)
  }

  def postSnapshot(state: State) = {}
}

