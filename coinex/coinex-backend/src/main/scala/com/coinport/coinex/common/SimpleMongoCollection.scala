package com.coinport.coinex.common

import com.mongodb.casbah._
import com.coinport.coinex.serializers.ThriftEnumJson4sSerialization
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON

abstract class SimpleMongoCollection[T <: AnyRef, S <: T](implicit man: Manifest[S]) {
  import ThriftEnumJson4sSerialization._
  import org.json4s.native.Serialization.{ read, write }

  val coll: MongoCollection
  val DATA = "data"
  val ID = "_id"

  def extractId(obj: T): Long

  def get(id: Long) = coll.findOne(MongoDBObject(ID -> id)) map { json => read[S](json.get(DATA).toString) }

  def put(data: T) = coll += MongoDBObject(ID -> extractId(data), DATA -> JSON.parse(write(data)))
}
