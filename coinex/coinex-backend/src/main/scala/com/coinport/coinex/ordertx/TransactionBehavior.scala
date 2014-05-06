package com.coinport.coinex.ordertx

import com.mongodb.casbah.Imports._
import com.coinport.coinex.data._
import Implicits._
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.serializers.ThriftBinarySerializer

trait TransactionBehavior {
  val TID = "_id"
  val TAKER_ID = "tid"
  val MAKER_ID = "mid"
  val TAKER_ORDER_ID = "toid"
  val MAKER_ORDER_ID = "moid"
  val TIMESTAMP = "@"
  val MARKET_SIDE = "m"
  val TRANSACTION = "t"

  val coll: MongoCollection
  val converter = new ThriftBinarySerializer

  def addItem(item: Transaction) = coll.insert(toBson(item))

  def countItems(q: QueryTransaction) = if (q.getCount) coll.count(mkQuery(q)) else 0L

  def getItems(q: QueryTransaction): Seq[Transaction] =
    if (q.getCount) Nil
    else coll.find(mkQuery(q)).sort(DBObject(TID -> -1)).skip(q.cursor.skip).limit(q.cursor.limit).map(toClass(_)).toSeq

  private def toBson(t: Transaction) = {
    MongoDBObject(
      TID -> t.id, TAKER_ID -> t.takerUpdate.current.userId, TAKER_ORDER_ID -> t.takerUpdate.current.id,
      MAKER_ID -> t.makerUpdate.current.userId, MAKER_ORDER_ID -> t.makerUpdate.current.id,
      MARKET_SIDE -> t.side, TIMESTAMP -> t.timestamp, TIMESTAMP -> t.timestamp,
      TRANSACTION -> converter.toBinary(t))
  }

  private def toClass(obj: MongoDBObject) =
    converter.fromBinary(obj.getAs[Array[Byte]](TRANSACTION).get, Some(classOf[Transaction.Immutable])).asInstanceOf[Transaction]

  private def mkQuery(q: QueryTransaction): MongoDBObject = {
    var query = MongoDBObject()
    if (q.tid.isDefined) query ++= MongoDBObject(TID -> q.tid.get)
    if (q.oid.isDefined) query ++= $or(TAKER_ORDER_ID -> q.oid.get, MAKER_ORDER_ID -> q.oid.get)
    if (q.uid.isDefined) query ++= $or(TAKER_ID -> q.uid.get, MAKER_ID -> q.uid.get)
    if (q.side.isDefined) query ++= {
      val qs = q.side.get
      if (qs.bothSide) $or(MARKET_SIDE -> q.side.get.side, MARKET_SIDE -> q.side.get.side.reverse)
      else MongoDBObject(MARKET_SIDE -> q.side.get.side)
    }
    query
  }
}