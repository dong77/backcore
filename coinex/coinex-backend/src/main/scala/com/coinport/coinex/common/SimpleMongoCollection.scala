package com.coinport.coinex.common

import com.mongodb.casbah._
import com.coinport.coinex.serializers._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON

sealed trait SimpleMongoCollection[T <: AnyRef] {
  val coll: MongoCollection
  val DATA = "data"
  val ID = "_id"

  def extractId(obj: T): Long
  def get(id: Long): Option[T]
  def put(data: T): Unit
  // def delete(id: Long) = coll -= MongoDBObject(ID -> id)
}

abstract class SimpleJsonMongoCollection[T <: AnyRef, S <: T](implicit man: Manifest[S]) extends SimpleMongoCollection[T] {
  import ThriftEnumJson4sSerialization._
  import org.json4s.native.Serialization.{ read, write }
  def get(id: Long) = coll.findOne(MongoDBObject(ID -> id)) map { json => read[S](json.get(DATA).toString) }
  def put(data: T) = coll += MongoDBObject(ID -> extractId(data), DATA -> JSON.parse(write(data)))
}

abstract class SimpleBinaryMongoCollection[T <: AnyRef, S <: T](implicit man: Manifest[S]) extends SimpleMongoCollection[T] {
  val serializer = new ThriftBinarySerializer
  def get(id: Long) = coll.findOne(MongoDBObject(ID -> id)) map { json =>
    serializer.fromBinary(json.get(DATA).asInstanceOf[Array[Byte]], Some(man.runtimeClass)).asInstanceOf[T]
  }
  def put(data: T) = coll += MongoDBObject(ID -> extractId(data), DATA -> serializer.toBinary(data))
}