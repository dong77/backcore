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
  val profiles = new SimpleJsonMongoCollection[UserProfile, UserProfile.Immutable] {
    val coll = MongoConnection(ip, 27017)("coinex_readers")("user_profiles")
    def extractId(profile: UserProfile) = profile.id
  }
  println("start to test")
  "fix bug " should {
    "find bug" in {
      profiles.find(MongoDBObject(), 0, 200000).foreach {
        u =>
          println("user profile>>>>>>>>" + u)
          u.depositAddresses match {
            case Some(map) =>
              println("address map>>>>>>>>" + map)
              map.get(Currency.Nxt) match {
                case Some(addr) =>
                  println(addr)
                  if (addr.startsWith("//"))
                    println(u.email)
                case None =>
              }
            case None =>
          }
      }
      success
    }
  }

}
