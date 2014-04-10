package com.coinport.coinex.ordertx

import com.mongodb.casbah.Imports._
import com.coinport.coinex.data._
import com.coinport.coinex.data.MarketMap._
import Implicits._

trait TransactionMongoHandler {
  val TID = "_id"
  val TAKER_ID = "tid"
  val MAKER_ID = "mid"
  val TAKER_ORDER_ID = "toid"
  val MAKER_ORDER_ID = "moid"
  val VOLUME = "vol"
  val AMOUNT = "amt"
  val PRICE = "price"
  val TIMESTAMP = "@"
  val SIDE = "s"
  val MARKET = "m"

  val coll: MongoCollection

  def addItem(item: TransactionItem) = coll.insert(toBson(item))

  def countItems(q: QueryTransaction) = if (q.getCount) coll.count(mkQuery(q)) else 0L

  def getItems(q: QueryTransaction): Seq[TransactionItem] =
    if (q.getCount) Nil
    else coll.find(mkQuery(q)).sort(DBObject(TID -> -1)).skip(q.cursor.skip).limit(q.cursor.limit).map(toClass(_)).toSeq

  private def toBson(item: TransactionItem) = {
    val market = item.side.market
    MongoDBObject(
      TID -> item.tid, TAKER_ID -> item.taker, MAKER_ID -> item.maker, TAKER_ORDER_ID -> item.tOrder,
      MAKER_ORDER_ID -> item.mOrder, VOLUME -> item.volume, AMOUNT -> item.amount, PRICE -> item.price,
      MARKET -> getValue(market), SIDE -> market.direction, TIMESTAMP -> item.timestamp)
  }

  private def toClass(obj: MongoDBObject) = TransactionItem(
    tid = obj.getAsOrElse(TID, -1), taker = obj.getAsOrElse(TAKER_ID, -1), maker = obj.getAsOrElse(MAKER_ID, -1),
    amount = obj.getAsOrElse(AMOUNT, -1), price = obj.getAsOrElse(PRICE, -1),
    side = getSide(obj.getAsOrElse(MARKET, 0)).getMarketSide(obj.getAsOrElse(SIDE, true)),
    tOrder = obj.getAsOrElse(TAKER_ORDER_ID, -1), mOrder = obj.getAsOrElse(MAKER_ORDER_ID, -1),
    volume = obj.getAsOrElse(VOLUME, -1), timestamp = obj.getAsOrElse(TIMESTAMP, -1))

  private def mkQuery(q: QueryTransaction): MongoDBObject = {
    var query = MongoDBObject()
    if (q.tid.isDefined) query = query ++ (TID -> q.tid.get)
    if (q.oid.isDefined) query = query ++ $or(TAKER_ORDER_ID -> q.oid.get, MAKER_ORDER_ID -> q.oid.get)
    if (q.uid.isDefined) query = query ++ $or(TAKER_ID -> q.uid.get, MAKER_ID -> q.uid.get)
    if (q.side.isDefined) query = {
      val querySide = q.side.get
      val market = querySide.side.market
      if (querySide.bothSide) query ++ (MARKET -> getValue(market))
      else query ++ (MARKET -> getValue(querySide.side.market), SIDE -> market.direction)
    }
    query
  }
}
