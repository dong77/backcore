package com.coinport.coinex.opendata

import akka.event.LoggingReceive
import akka.persistence._
import com.coinport.coinex.data._
import com.coinport.coinex.util.MHash
import com.coinport.coinex.serializers.ThriftJsonSerializer
import Implicits._
import com.mongodb.util.JSON
import com.mongodb.casbah.Imports._
import com.coinport.coinex.common.support.SnapshotSupport
import com.coinport.coinex.common.Manager

// This view is not defined for querying data.
abstract class EventExportToMongoView(db: MongoDB, pid: String) extends View with SnapshotSupport {
  override val processorId = pid
  override val viewId = pid + "_export"
  val manager = new EventExportToMongoManager

  val eventColl = db(pid + "_events")
  val metaColl = db(pid + "_metadata")

  def shouldExport(event: AnyRef): Boolean

  def receive = LoggingReceive {
    case cmd: TakeSnapshotNow => takeSnapshot(cmd) {
      manager.increaseSnapshotIndex()
      saveSnapshot(manager.getSnapshot)
      metaColl += manager.getSnapshotAsJSon
      log.info("===== export data generated new snapshot: " + manager.getSnapshot)
    }

    case Persistent(m: AnyRef, _) if shouldExport(m) =>
      eventColl += manager.generateJson(m)

    case m: QueryExportToMongoState => sender ! manager.getSnapshot
  }
}

class EventExportToMongoManager extends Manager[TExportToMongoState] {
  private var state = TExportToMongoState(0, 0, "0" * 32, 0)
  private val serializer = new ThriftJsonSerializer

  def getSnapshot = state
  def loadSnapshot(snapshot: TExportToMongoState) = state = snapshot

  def increaseSnapshotIndex() = {
    state = state.copy(snapshotIndex = state.snapshotIndex + 1, lastSnapshotTimestamp = System.currentTimeMillis)
  }

  def getSnapshotAsJSon = {
    val data = JSON.parse(new String(serializer.toBinary(state)))
    MongoDBObject("_id" -> state.snapshotIndex, "metadata" -> data)
  }

  def generateJson(m: AnyRef) = {
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

    state = state.copy(index = state.index + 1, hash = hash)
    jsonWithHash
  }
}

