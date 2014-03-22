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
import scala.Predef._

case class UserState(numUsers: Long = 0,
    profileMap: Map[Long, UserProfile] = Map.empty[Long, UserProfile],
    passwordResetTokenMap: Map[String, Long] = Map.empty[String, Long]) {
  def userExist(email: String): Boolean = userExist(Hash.murmur3(email))
  def userExist(userId: Long): Boolean = profileMap.contains(userId)

  def addUserProfile(profile: UserProfile): UserState = {
    copy(numUsers = this.numUsers + 1, profileMap = this.profileMap + (profile.id -> profile))
  }

  def updateUserProfile(userId: Long)(updateOp: UserProfile => UserProfile): UserState = {
    assert(!profileMap.contains(userId))
    copy(profileMap = this.profileMap + (userId -> updateOp(profileMap(userId))))
  }

  def deletePasswordResetToken(token: String): UserState = {
    copy(passwordResetTokenMap = this.passwordResetTokenMap - token)
  }

  def addPasswordResetToken(token: String, userId: Long) = {
    copy(passwordResetTokenMap = this.passwordResetTokenMap + (token -> userId))
  }
}