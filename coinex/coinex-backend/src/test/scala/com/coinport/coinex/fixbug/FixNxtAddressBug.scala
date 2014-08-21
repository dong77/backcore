package com.coinport.coinex.fixbug

import com.mongodb.casbah.Imports._
import com.coinport.coinex.data.{ Currency, UserProfile }
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import org.specs2.mutable._

/**
 * Created by chenxi on 8/21/14.
 */
class FixNxtAddressBug extends Specification {

  //  val ip = "172.13.8.201"
  val ip = "localhost"
  val coll = MongoConnection(ip, 27017)("coinex_readers")("user_profiles")
  "fix bug " should {
    "find bug" in {
      var count = 0
      (1000000000 to 1000003000).foreach { id =>
        coll.findOne(MongoDBObject("_id" -> id)).foreach { u =>
          val data: DBObject = u.getAs[DBObject]("data").get
          if (data.containsField("depositAddresses")) {
            val list = data.getAs[MongoDBList]("depositAddresses").get
            list.foreach { a =>
              val b = a.asInstanceOf[DBObject]
              if (b.getAs[String]("_1").get == "Nxt" && b.getAs[String]("_2").get.startsWith("//")) {
                count = count + 1
                println(u.getAs[DBObject]("data").get.getAs[String]("email").get)
              }
            }
          }
        }
      }
      success
    }
  }
}
