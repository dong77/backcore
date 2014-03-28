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

  case class State(snapshotIndex: Long, index: Long, hash: String)

  var state = State(0, 0, "0" * 32)

  def receive = LoggingReceive {
    case TakeSnapshotNow => {
      saveSnapshot(state)
      postSnapshot(state)
      state = state.copy(snapshotIndex = state.snapshotIndex + 1)
    }

    case SnapshotOffer(meta, snapshot) => state =
      snapshot.asInstanceOf[State]

    case Persistent(m: AnyRef, _) =>
      val data = JSON.parse(new String(serializer.toBinary(m)))
      val event = m.getClass.getName.replace("com.coinport.coinex.data.", "").replace("$Immutable", "")

      val json = MongoDBObject(
        "_id" -> state.index,
        "snapshot" -> state.snapshotIndex,
        "prehash" -> state.hash,
        event -> data)

      val hash = Hash.sha1Base32(json.toString)
      val jsonWithHash = MongoDBObject(
        "_id" -> state.index,
        "snapshot" -> state.snapshotIndex,
        "prehash" -> state.hash,
        "hash" -> hash,
        event -> data)

      collection += jsonWithHash
      state = state.copy(index = state.index + 1, hash = hash)
  }

  def postSnapshot(state: State) = {}
}

