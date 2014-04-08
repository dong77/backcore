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
    passwordResetTokenMap: Map[String, Long] = Map.empty[String, Long],
    verificationTokenMap: Map[String, Long] = Map.empty[String, Long]) {

  def userExist(email: String): Boolean = userExist(MHash.murmur3(email))
  def userExist(userId: Long): Boolean = profileMap.contains(userId)

  def addUserProfile(profile: UserProfile): UserState = {
    copy(numUsers = numUsers + 1, profileMap = profileMap + (profile.id -> profile))
  }

  def updateUserProfile(userId: Long)(updateOp: UserProfile => UserProfile): UserState = {
    assert(!profileMap.contains(userId))
    copy(profileMap = profileMap + (userId -> updateOp(profileMap(userId))))
  }

  def deletePasswordResetToken(token: String): UserState = {
    copy(passwordResetTokenMap = passwordResetTokenMap - token)
  }

  def addPasswordResetToken(token: String, userId: Long) = {
    copy(passwordResetTokenMap = passwordResetTokenMap + (token -> userId))
  }

  def deleteVerificationToken(token: String): UserState = {
    copy(verificationTokenMap = verificationTokenMap - token)
  }

  def addVerificationToken(token: String, userId: Long) = {
    copy(verificationTokenMap = verificationTokenMap + (token -> userId))
  }
}
