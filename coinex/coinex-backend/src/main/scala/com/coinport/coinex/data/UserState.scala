/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import scala.collection.immutable.Map
import com.coinport.coinex.util._
import scala.util.{ Try, Success, Failure }
import scala.Predef._

object UserState {
  def emailToId(email: String) = Hash.murmur3(email)

  def computePaswordHash(profile: UserProfile, password: String) = {
    def regulate(s: String) = s.stripMargin.toLowerCase
    Hash.sha256("%s~%s~%s~%s".format(
      regulate(profile.email), regulate(profile.realName), regulate(profile.nationalId), password.stripMargin))
  }
}

case class UserState(numUsers: Long = 0,
    profileMap: Map[Long, UserProfile] = Map.empty[Long, UserProfile],
    passwordResetTokenMap: Map[String, Long] = Map.empty[String, Long]) {
  def userExist(email: String): Boolean = userExist(Hash.murmur3(email))
  def userExist(userId: Long): Boolean = profileMap.contains(userId)

  def addUser(profile: UserProfile, password: String): UserState = {

    val userId = Hash.murmur3(profile.email)
    assert(!profileMap.contains(userId))
    val updated = profile.copy(id = userId)

    copy(numUsers = this.numUsers + 1, profileMap = this.profileMap + (userId -> updated))
  }

  def setPasswordHash(userId: Long, newPasswordHash: Array[Byte]): UserState = updateUser(userId) {
    _.copy(passwordHash = Some(newPasswordHash), passwordResetToken = None)
  }

  def setUserStatus(userId: Long, status: UserStatus) = updateUser(userId) {
    _.copy(status = status)
  }

  private def updateUser(userId: Long)(updateOp: UserProfile => UserProfile): UserState = {
    assert(!profileMap.contains(userId))
    copy(profileMap = this.profileMap + (userId -> updateOp(profileMap(userId))))
  }
}