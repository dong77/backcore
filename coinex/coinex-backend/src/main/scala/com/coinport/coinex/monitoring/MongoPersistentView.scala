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

class MongoPersistentView(pid: String) extends ExtendedView {
  override val processorId = pid
  override val viewId = pid + "_mongop"

  val serializer = new ThriftJsonSerializer
  val mongoConn = MongoConnection("localhost", 27017)
  val mongoColl = mongoConn("coinex_export")(pid)

  case class State(index: Long, hash: String)

  var state = State(0, "0" * 40)

  def receive = LoggingReceive {
    case TakeSnapshotNow => saveSnapshot(state)
    case SnapshotOffer(meta, snapshot) => state = snapshot.asInstanceOf[State]

    case Persistent(m: AnyRef, _) =>
      val data = new String(serializer.toBinary(m))
      val hash = Hash.sha1Base32(data)
      val obj = MongoDBObject("_id" -> state.index, "prehash" -> state.hash, "hash" -> hash, "data" -> JSON.parse(data))
      mongoColl += obj
      state = State(state.index + 1, hash)
  }
}

