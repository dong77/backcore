package com.coinport.coinex.monitoring

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView
import akka.persistence._
import com.coinport.coinex.data._
import com.coinport.coinex.util.MHash
import com.coinport.coinex.serializers.ThriftJsonSerializer
import Implicits._
import com.mongodb.util.JSON
import com.mongodb.casbah.Imports._

// This view is not defined for querying data.
class EventExportToMongoView(db: MongoDB, pid: String) extends ExtendedView {
  override val processorId = pid
  override val viewId = pid + "_mongop"

  // This serializer cannot deserialize json to thrift case classes properly.
  val serializer = new ThriftJsonSerializer
  val collection = db(pid + "_events")

  var state = TExportToMongoState(0, 0, "0" * 32)

  def receive = LoggingReceive {
    case TakeSnapshotNow => {
      saveSnapshot(state)
      postSnapshot(state)
      state = state.copy(snapshotIndex = state.snapshotIndex + 1)
    }

    case SnapshotOffer(meta, snapshot) => state =
      snapshot.asInstanceOf[TExportToMongoState]

    case Persistent(m: AnyRef, _) =>
      val data = JSON.parse(new String(serializer.toBinary(m)))
      val event = m.getClass.getName.replace("com.coinport.coinex.data.", "").replace("$Immutable", "")

      val json = MongoDBObject(
        "_id" -> state.index,
        "snapshot" -> state.snapshotIndex,
        "prehash" -> state.hash,
        event -> data)

      val hash = MHash.sha1Base32(json.toString)
      val jsonWithHash = MongoDBObject(
        "_id" -> state.index,
        "snapshot" -> state.snapshotIndex,
        "prehash" -> state.hash,
        "hash" -> hash,
        event -> data)

      collection += jsonWithHash
      state = state.copy(index = state.index + 1, hash = hash)
  }

  def postSnapshot(state: TExportToMongoState) = {}
}

