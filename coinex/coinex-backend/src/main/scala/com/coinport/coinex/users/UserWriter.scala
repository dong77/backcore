package com.coinport.coinex.users

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.mongodb.casbah.MongoDB
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.common.PersistentId._
import Implicits._

// This view persists user manager state into MongoDB but also keeps an inmemory copy of the state.
// THis view shall not serve any queries.
class UserWriter(db: MongoDB, userManagerSecret: String) extends ExtendedView {
  override val processorId = USER_PROCESSOR <<
  override val viewId = USER_WRITER_VIEW <<

  val totpAuthenticator = new GoogleAuthenticator
  val manager = new UserManager(totpAuthenticator, userManagerSecret)

  def receive = LoggingReceive {
    case Persistent(m, seq) => updateState(m)
  }

  def updateState(event: Any) = event match {
    case DoRegisterUser(profile, _) => profiles.put(manager.registerUser(profile))
    case DoUpdateUserProfile(profile) => profiles.put(profile)
    case DoRequestPasswordReset(email) => profiles.put(manager.requestPasswordReset(email, lastSequenceNr))
    case DoResetPassword(email, password, token) => profiles.put(manager.resetPassword(email, password, token))
  }

  val profiles = new SimpleJsonMongoCollection[UserProfile, UserProfile.Immutable] {
    val coll = db("user_profiles")
    def extractId(profile: UserProfile) = profile.id
  }
}