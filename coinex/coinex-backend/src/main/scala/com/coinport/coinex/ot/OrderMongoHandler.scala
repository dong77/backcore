package com.coinport.coinex.ot

import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import com.coinport.coinex.serializers.ThriftBinarySerializer
import com.coinport.coinex.ot.MarketSideMap._

trait OrderMongoHandler {
  val OID = "_id"
  val UID = "uid"
  val IN_AMOUNT = "ia"
  val OUT_AMOUNT = "oa"
  val ORIGIN_ORDER = "oo"
  val SIDE = "ss"
  val STATUS = "s"
  val CREATED_TIME = "c@"
  val UPDATED_TIME = "u@"

  val converter = new ThriftBinarySerializer

  val coll: MongoCollection

  def addItem(item: OrderInfo) = coll.insert(toBson(item))

  def updateItem(orderId: Long, inAmount: Long, outAmount: Long, status: Int, side: MarketSide, timestamp: Long) =
    coll.update(MongoDBObject(OID -> orderId), $set(IN_AMOUNT -> inAmount, OUT_AMOUNT -> outAmount, STATUS -> status,
      UPDATED_TIME -> timestamp, SIDE -> getValue(side)), false, false)

  def cancelItem(orderId: Long) =
    coll.update(MongoDBObject(OID -> orderId), $set(STATUS -> OrderStatus.Cancelled), false, false)

  def countItems(q: QueryOrder): Long = if (q.getCount) coll.count(mkQuery(q)) else 0L

  def getItems(q: QueryOrder): Seq[OrderInfo] =
    if (q.getCount) Nil
    else coll.find(mkQuery(q)).sort(DBObject(OID -> -1)).skip(q.cursor.skip).limit(q.cursor.limit).map(toClass(_)).toSeq

  private def toBson(item: OrderInfo) = MongoDBObject(
    OID -> item.order.id, UID -> item.order.userId, ORIGIN_ORDER -> converter.toBinary(item.order),
    IN_AMOUNT -> item.inAmount, OUT_AMOUNT -> item.outAmount, SIDE -> getValue(item.side),
    CREATED_TIME -> item.order.timestamp.getOrElse(0), UPDATED_TIME -> item.lastTxTimestamp.getOrElse(0), STATUS -> item.status)

  private def toClass(obj: MongoDBObject) = OrderInfo(
    order = obj.getAsOrElse(ORIGIN_ORDER, null), inAmount = obj.getAsOrElse(IN_AMOUNT, -1), outAmount = obj.getAsOrElse(OUT_AMOUNT, -1),
    side = getSide(obj.getAsOrElse(SIDE, 0)), lastTxTimestamp = obj.getAs[Long](UPDATED_TIME),
    status = OrderStatus.get(obj.getAsOrElse(STATUS, 0)).getOrElse(OrderStatus.Pending))

  private def mkQuery(q: QueryOrder) = {
    var query = MongoDBObject()
    if (q.oid.isDefined) query = query ++ (OID -> q.oid.get)
    if (q.uid.isDefined) query = query ++ (UID -> q.uid.get)
    if (q.side.isDefined) query = query ++ (SIDE -> getValue(q.side.get))
    if (q.status.isDefined) query = query ++ (STATUS -> q.status.get)
    query
  }

}
