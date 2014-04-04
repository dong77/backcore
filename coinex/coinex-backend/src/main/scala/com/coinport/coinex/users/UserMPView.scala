package com.coinport.coinex.users

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView
import akka.persistence.Persistent
import com.coinport.coinex.data._
import com.mongodb.casbah.MongoDB
import com.coinport.coinex.common.SimpleJsonMongoCollection

// This view persists user manager state into MongoDB but also keeps an inmemory copy of the state.
// THis view shall not serve any queries.
class UserMPView(db: MongoDB, userManagerSecret: String) extends ExtendedView {
  override val processorId = "coinex_up"
  override val viewId = "user_mpview"

  val manager = new UserManager(userManagerSecret)

  def receive = LoggingReceive {
    case Persistent(m, seq) => updateState(m)
  }

  def updateState(event: Any) = event match {
    case DoRegisterUser(profile, _) =>
      profiles.put(manager.registerUser(profile))
    case DoRequestPasswordReset(email) =>
      profiles.put(manager.requestPasswordReset(email, lastSequenceNr))
    case DoResetPassword(email, password, token) =>
      profiles.put(manager.resetPassword(email, password, token))
  }

  val profiles = new SimpleJsonMongoCollection[UserProfile, UserProfile.Immutable] {
    val coll = db("user_profiles")
    def extractId(profile: UserProfile) = profile.id
  }
}