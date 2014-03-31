/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import com.mongodb.casbah.Imports._
import com.twitter.bijection.scrooge.BinaryScalaCodec

case class OrderDataState(coll: MongoCollection) {
  val OID = "_id"
  val UID = "uid"
  val IN_AMOUNT = "ia"
  val OUT_AMOUNT = "oa"
  val ORIGIN_ORDER = "oo"
  val SAME_SIDE = "ss"
  val STATUS = "s"
  val TIMESTAMP = "@"

  implicit def toBytes(o: Order): Array[Byte] = BinaryScalaCodec(Order)(o)
  implicit def fromBytes(bytes: Array[Byte]) = BinaryScalaCodec(Order).invert(bytes).get

  def addItem(item: OrderItem) = coll.insert(toBson(item))

  def updateItem(orderId: Long, inAmount: Long, outAmount: Long, status: Int, timestamp: Long) =
    coll.update(MongoDBObject(OID -> orderId), $set(IN_AMOUNT -> inAmount, OUT_AMOUNT -> outAmount, STATUS -> status,
      TIMESTAMP -> timestamp, SAME_SIDE -> false), false, false)

  def cancelItem(orderId: Long) =
    coll.update(MongoDBObject(OID -> orderId), $set(STATUS -> OrderStatus.Cancelled), false, false)

  def countItems(q: QueryOrder): Long = if (q.getCount) coll.count(mkQuery(q)) else 0L

  def getItems(q: QueryOrder): Seq[OrderItem] =
    if (q.getCount) Nil
    else coll.find(mkQuery(q)).sort(DBObject(OID -> -1)).skip(q.cursor.skip).limit(q.cursor.limit).map(toClass(_)).toSeq

  private def mkQuery(q: QueryOrder) = {
    var query = MongoDBObject()

    if (q.oid.isDefined) query = query ++ (OID -> q.oid.get)
    if (q.uid.isDefined) query = query ++ (UID -> q.uid.get)
    if (q.sameSide.isDefined) query = query ++ (SAME_SIDE -> q.sameSide.get)
    if (q.status.isDefined) query = query ++ (STATUS -> q.status.get)

    query
  }

  private def toBson(item: OrderItem) = MongoDBObject(
    OID -> item.oid, UID -> item.uid, ORIGIN_ORDER -> item.originOrder, IN_AMOUNT -> item.inAmount, OUT_AMOUNT -> item.outAmount,
    SAME_SIDE -> item.sameSide, TIMESTAMP -> item.timestamp, STATUS -> item.status)

  private def toClass(obj: MongoDBObject) = OrderItem(
    oid = obj.getAsOrElse(OID, -1), uid = obj.getAsOrElse(UID, -1), originOrder = obj.getAsOrElse(ORIGIN_ORDER, null),
    inAmount = obj.getAsOrElse(IN_AMOUNT, -1), outAmount = obj.getAsOrElse(OUT_AMOUNT, -1),
    sameSide = obj.getAsOrElse(SAME_SIDE, false), timestamp = obj.getAsOrElse(TIMESTAMP, -1), status = obj.getAsOrElse(STATUS, 0))
}
