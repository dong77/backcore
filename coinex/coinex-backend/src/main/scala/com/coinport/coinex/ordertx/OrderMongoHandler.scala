package com.coinport.coinex.ordertx

import com.coinport.coinex.data._
import com.mongodb.casbah.Imports._
import com.coinport.coinex.serializers.ThriftBinarySerializer
import Implicits._

trait OrderMongoHandler {
  val OID = "_id"
  val UID = "uid"
  val IN_AMOUNT = "ia"
  val ORIGIN_ORDER = "oo"
  val STATUS = "st"
  val CREATED_TIME = "c@"
  val UPDATED_TIME = "u@"
  val MARKET = "m"
  val SIDE = "s"
  val QUANTITY = "q"

  val converter = new ThriftBinarySerializer

  val coll: MongoCollection

  def addItem(item: OrderInfo, quantity: Long) = coll.insert(toBson(item))

  def updateItem(orderId: Long, inAmount: Long, quantity: Long, status: Int, side: MarketSide, timestamp: Long) =
    coll.update(MongoDBObject(OID -> orderId), $set(IN_AMOUNT -> inAmount, QUANTITY -> quantity, STATUS -> status,
      UPDATED_TIME -> timestamp, SIDE -> side.ordered), false, false)

  def cancelItem(orderId: Long) =
    coll.update(MongoDBObject(OID -> orderId), $set(STATUS -> OrderStatus.Cancelled.getValue()), false, false)

  def countItems(q: QueryOrder): Long = if (q.getCount) coll.count(mkQuery(q)) else 0L

  def getItems(q: QueryOrder): Seq[OrderInfo] = {
    if (q.getCount) Nil
    else coll.find(mkQuery(q)).sort(DBObject(OID -> -1)).skip(q.cursor.skip).limit(q.cursor.limit).map(toClass(_)).toSeq
  }

  private def toBson(item: OrderInfo) = {
    val side = item.side
    val obj = MongoDBObject(
      OID -> item.order.id, UID -> item.order.userId, ORIGIN_ORDER -> converter.toBinary(item.order),
      IN_AMOUNT -> item.inAmount, QUANTITY -> item.order.quantity, MARKET -> side.market.toString,
      SIDE -> side.ordered, CREATED_TIME -> item.order.timestamp.getOrElse(0), STATUS -> item.status.getValue())

    if (item.lastTxTimestamp.isDefined) obj ++ (UPDATED_TIME -> item.lastTxTimestamp.get)
    else obj
  }

  private def toClass(obj: MongoDBObject) = {
    val originalOrder = converter.fromBinary(obj.getAsOrElse(ORIGIN_ORDER, null), Some(classOf[Order.Immutable])).asInstanceOf[Order]
    OrderInfo(
      order = originalOrder, inAmount = obj.getAsOrElse[Long](IN_AMOUNT, 0),
      outAmount = originalOrder.quantity - obj.getAsOrElse[Long](QUANTITY, 0),
      side = obj.getAsOrElse[String](MARKET, "").getMarketSide(obj.getAsOrElse[Boolean](SIDE, true)),
      lastTxTimestamp = obj.getAs[Long](UPDATED_TIME),
      status = OrderStatus.get(obj.getAsOrElse[Int](STATUS, 0)).getOrElse(OrderStatus.Pending))
  }

  private def mkQuery(q: QueryOrder) = {
    var query = MongoDBObject()
    if (q.oid.isDefined) query = query ++ (OID -> q.oid.get)
    if (q.uid.isDefined) query = query ++ (UID -> q.uid.get)
    if (q.side.isDefined) query = {
      val querySide = q.side.get
      val side = querySide.side
      if (querySide.bothSide) query ++ (MARKET -> side.market.toString)
      else query ++ (MARKET -> side.market.toString, SIDE -> side.ordered)
    }
    if (q.status.isDefined) query = query ++ (STATUS -> q.status.get)
    query
  }
}
