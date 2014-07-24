package com.coinport.coinex.users

import com.coinport.coinex.common.ExtendedView
import com.coinport.coinex.data._
import com.mongodb.casbah.MongoDB
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.common.PersistentId._
import Implicits._

class UserReader(db: MongoDB) {
  val profiles = new SimpleJsonMongoCollection[UserProfile, UserProfile.Immutable] {
    val coll = db(UserWriter.collName)
    def extractId(profile: UserProfile) = profile.id
  }

  def getEmailByUid(uid: Long) = {
    profiles.get(uid).map(_.email).getOrElse("")
  }
}
