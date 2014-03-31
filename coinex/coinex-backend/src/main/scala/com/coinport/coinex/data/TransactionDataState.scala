/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import com.mongodb.casbah.Imports._

case class TransactionDataState(coll: MongoCollection) {
  val TID = "_id"
  val TAKER_ID = "tid"
  val MAKER_ID = "mid"
  val TAKER_ORDER_ID = "toid"
  val MAKER_ORDER_ID = "moid"
  val VOLUME = "vol"
  val AMOUNT = "amt"
  val PRICE = "price"
  val SAME_SIDE = "side"
  val TIMESTAMP = "@"

  def addItem(item: TransactionItem) = coll.insert(toBson(item))

  def countItems(q: QueryTransaction) = if (q.getCount) coll.count(mkQuery(q)) else 0L

  def getItems(q: QueryTransaction): Seq[TransactionItem] =
    if (q.getCount) Nil
    else coll.find(mkQuery(q)).sort(DBObject(TID -> -1)).skip(q.cursor.skip).limit(q.cursor.limit).map(toClass(_)).toSeq

  private def toBson(item: TransactionItem) = MongoDBObject(
    TID -> item.tid, TAKER_ID -> item.taker, MAKER_ID -> item.maker, TAKER_ORDER_ID -> item.tOrder,
    MAKER_ORDER_ID -> item.mOrder, VOLUME -> item.volume, AMOUNT -> item.amount, PRICE -> item.price,
    SAME_SIDE -> item.sameSide, TIMESTAMP -> item.timestamp)

  private def toClass(obj: MongoDBObject) = TransactionItem(
    tid = obj.getAsOrElse(TID, -1), taker = obj.getAsOrElse(TAKER_ID, -1), maker = obj.getAsOrElse(MAKER_ID, -1),
    amount = obj.getAsOrElse(AMOUNT, -1), price = obj.getAsOrElse(PRICE, -1), sameSide = obj.getAsOrElse(SAME_SIDE, false),
    tOrder = obj.getAsOrElse(TAKER_ORDER_ID, -1), mOrder = obj.getAsOrElse(MAKER_ORDER_ID, -1),
    volume = obj.getAsOrElse(VOLUME, -1), timestamp = obj.getAsOrElse(TIMESTAMP, -1))

  private def mkQuery(q: QueryTransaction): MongoDBObject = {
    var query = MongoDBObject()

    if (q.tid.isDefined) query = query ++ (TID -> q.tid.get)
    if (q.oid.isDefined) query = query ++ $or(TAKER_ORDER_ID -> q.oid.get, MAKER_ORDER_ID -> q.oid.get)
    if (q.uid.isDefined) query = query ++ $or(TAKER_ID -> q.uid.get, MAKER_ID -> q.uid.get)
    if (q.sameSide.isDefined) query = query ++ (SAME_SIDE -> q.sameSide.get)

    query
  }
}
