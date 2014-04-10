/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * MarketManager is the maintainer of a Market. It executes new orders before
 * they are added into a market as pending orders. As execution results, a list
 * of Transactions are generated and returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 *
 * Note this class does NOT depend on event-sourcing framework we choose. Please
 * keep it plain old scala/java.
 */

package com.coinport.coinex.users

import scala.collection.mutable.Map
import com.coinport.coinex.data._
import com.coinport.coinex.common.AbstractManager
import com.coinport.coinex.util._
import com.google.common.io.BaseEncoding

class UserManager(googleAuthenticator: GoogleAuthenticator, secret: String = "") extends AbstractManager[TUserState] {
  // Internal mutable state ----------------------------------------------
  var numUsers = 0L
  val profileMap: Map[Long, UserProfile] = Map.empty[Long, UserProfile]
  val passwordResetTokenMap: Map[String, Long] = Map.empty[String, Long]
  val verificationTokenMap: Map[String, Long] = Map.empty[String, Long]

  // Thrift conversions     ----------------------------------------------
  def getSnapshot = TUserState(profileMap.clone, passwordResetTokenMap.clone, verificationTokenMap.clone, numUsers)

  def loadSnapshot(snapshot: TUserState) = {
    profileMap.clear; profileMap ++= snapshot.profileMap
    passwordResetTokenMap.clear; passwordResetTokenMap ++= snapshot.profileMap
    verificationTokenMap.clear; verificationTokenMap ++= snapshot.profileMap
    numUsers = snapshot.numUsers
  }

  // Business logics      ----------------------------------------------
  def regulateProfile(profile: UserProfile, password: String, salt: Long): UserProfile = {
    val id = computeUserId(profile.email)
    val verificationToken = computeHexToken(salt, profile.email)
    val passwordHash = computePassword(id, profile.email, password)
    val googleAuthenticatorSecret = googleAuthenticator.createSecret

    profile.copy(
      id = id,
      emailVerified = false,
      passwordHash = Some(passwordHash),
      passwordResetToken = None,
      verificationToken = Some(verificationToken),
      loginToken = None,
      googleAuthenticatorSecret = None,
      status = UserStatus.Normal)
  }

  def registerUser(profile: UserProfile): UserProfile = {
    addUserProfile(profile)
    addVerificationToken(profile.verificationToken.get, profile.id)
    profile
  }

  def updateUser(profile: UserProfile): UserProfile = {
    addUserProfile(profile)
    profile
  }

  def requestPasswordReset(email: String, salt: Long): UserProfile = {
    val id = computeUserId(email)
    val profile = profileMap(id)
    val token = computeHexToken(salt, email)
    val updatedProfile = profile.copy(passwordResetToken = Some(token))
    updateUserProfile(id)(_ => updatedProfile)
    addPasswordResetToken(token, id)
    updatedProfile
  }

  def resetPassword(email: String, password: String, passwordResetToken: Option[String]): UserProfile = {
    val id = computeUserId(email)
    val profile = profileMap(id)
    profile.passwordResetToken foreach { deletePasswordResetToken }
    val passwordHash = computePassword(profile.id, profile.email, password)
    val updatedProfile = profile.copy(passwordHash = Some(passwordHash), passwordResetToken = None)
    updateUserProfile(id)(_ => updatedProfile)
    updatedProfile
  }

  def checkLogin(email: String, password: String): Either[ErrorCode, UserProfile] =
    getUser(email) match {
      case None => Left(ErrorCode.UserNotExist)
      case Some(profile) =>
        val passwordHash = computePassword(profile.id, profile.email, password)
        if (Some(passwordHash) == profile.passwordHash) Right(profile)
        else Left(ErrorCode.PasswordNotMatch)
    }

  private val userIdSecret = MHash.sha256Base64(secret + "userIdSecret")
  private val passwordSecret = MHash.sha256Base64(secret + "passwordSecret")
  private val hexTokenSecret = MHash.sha256Base64(secret + "hexTokenSecret")
  private val numericTokenSecret = MHash.sha256Base64(secret + "numericTokenSecret")

  private def computeUserId(email: String) = MHash.sha256ThenMurmur3(email + userIdSecret)

  private def computePassword(userId: Long, email: String, password: String) =
    MHash.sha256Base64(email + passwordSecret + MHash.sha256Base64(userId + password.trim + passwordSecret))

  private def computeHexToken(salt: Long, email: String): String =
    MHash.sha256Base32(email + salt + hexTokenSecret).substring(0, 40)

  private def newNumericToken(salt: Long, email: String): String = {
    val num = MHash.sha256ThenMurmur3(email + salt + numericTokenSecret)
    "%04d".format(Math.abs(num)).substring(0, 6)

  }

  def getUser(email: String) = profileMap.get(computeUserId(email))
  def isEmailRegistered(email: String) = getUser(email).isDefined
  def userExist(email: String): Boolean = userExist(MHash.murmur3(email))
  def userExist(userId: Long): Boolean = profileMap.contains(userId)

  def addUserProfile(profile: UserProfile) = {
    numUsers += 1
    profileMap += profile.id -> profile
  }

  def updateUserProfile(userId: Long)(updateOp: UserProfile => UserProfile) = {
    assert(!profileMap.contains(userId))
    profileMap += userId -> updateOp(profileMap(userId))
  }

  def deletePasswordResetToken(token: String) = {
    passwordResetTokenMap -= token
  }

  def addPasswordResetToken(token: String, userId: Long) = {
    passwordResetTokenMap += token -> userId
  }

  def deleteVerificationToken(token: String) = {
    verificationTokenMap -= token
  }

  def addVerificationToken(token: String, userId: Long) = {
    verificationTokenMap += token -> userId
  }
}
