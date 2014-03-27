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

import com.coinport.coinex.data._
import com.coinport.coinex.common.StateManager
import com.coinport.coinex.util._
import com.google.common.io.BaseEncoding

class UserManager(secret: String = "") extends StateManager[UserState] {
  initWithDefaultState(UserState())

  def registerUser(profile: UserProfile, password: String, salt: Long): Either[RegisterationFailureReason, UserProfile] = {
    val email = regulate(profile.email)
    val id = computeUserId(email)
    state.profileMap.get(id) match {
      case Some(_) => Left(RegisterationFailureReason.EmailAlreadyRegistered)
      case None =>
        val verificationToken = newHexToken(salt, email)
        val updatedProfile: UserProfile = profile.copy(
          id = id,
          email = email,
          emailVerified = false,
          passwordResetToken = None,
          verificationToken = Some(verificationToken),
          loginToken = None,
          status = UserStatus.Normal)

        val passwordHash = computePassword(id, email, password)
        val profileWithPassword = updatedProfile.copy(passwordHash = Some(passwordHash))

        state = state.addUserProfile(profileWithPassword).addVerificationToken(verificationToken, id)
        Right(profileWithPassword)
    }
  }

  def checkLogin(email: String, password: String): Either[LoginFailureReason, UserProfile] = {
    val id = computeUserId(regulate(email))

    state.profileMap.get(id) match {
      case None => Left(LoginFailureReason.UserNotExist)
      case Some(profile) =>
        val passwordHash = computePassword(profile.id, profile.email, password)
        if (Some(passwordHash) == profile.passwordHash) Right(profile)
        else Left(LoginFailureReason.PasswordNotMatch)
    }
  }

  def requestPasswordReset(email: String, salt: Long): Either[RequestPasswordResetFailureReason, UserProfile] = {
    val e = regulate(email)
    val id = computeUserId(e)
    state.profileMap.get(id) match {
      case None => Left(RequestPasswordResetFailureReason.UserNotExist)
      case Some(profile) if profile.email != e => Left(RequestPasswordResetFailureReason.TokenNotUnique)
      case Some(profile) if profile.passwordResetToken.isDefined => Right(profile)
      case Some(profile) if profile.passwordResetToken.isEmpty =>

        val token = newHexToken(salt, e)
        val updatedProfile = profile.copy(passwordResetToken = Some(token))
        state = state.updateUserProfile(id)(_ => updatedProfile).addPasswordResetToken(token, id)
        Right(updatedProfile)
    }
  }

  def resetPassword(email: String, password: String, passwordResetToken: Option[String]): Either[ResetPasswordFailureReason, UserProfile] = {
    val id = computeUserId(regulate(email))
    state.profileMap.get(id) match {
      case None => Left(ResetPasswordFailureReason.UserNotExist)

      case Some(profile) if profile.passwordResetToken == passwordResetToken =>
        profile.passwordResetToken foreach { token => state = state.deletePasswordResetToken(token) }
        val passwordHash = computePassword(profile.id, profile.email, password)
        val updatedProfile = profile.copy(passwordHash = Some(passwordHash), passwordResetToken = None)
        state = state.updateUserProfile(id)(_ => updatedProfile)
        Right(updatedProfile)

      case _ => Left(ResetPasswordFailureReason.TokenNotMatch)
    }
  }

  private def regulate(s: String) = s.trim.toLowerCase

  private def sha256Of(text: String): String = BaseEncoding.base64.encode(Hash.sha256(text))
  private def sha256ThenMurmur3(text: String): Long = Hash.murmur3(BaseEncoding.base64.encode(Hash.sha256(text)))

  private val userIdSecret = sha256Of(secret + "userIdSecret")
  private val passwordSecret = sha256Of(secret + "passwordSecret")
  private val hexTokenSecret = sha256Of(secret + "hexTokenSecret")
  private val numericTokenSecret = sha256Of(secret + "numericTokenSecret")

  private def computeUserId(email: String): Long = sha256ThenMurmur3(email + userIdSecret)

  private def computePassword(userId: Long, email: String, password: String) =
    sha256Of(email + passwordSecret + sha256Of(userId + password.trim + passwordSecret))

  private def newHexToken(salt: Long, email: String): String =
    sha256Of(email + salt + hexTokenSecret)

  private def newNumericToken(salt: Long, email: String): String = {
    val num = sha256ThenMurmur3(email + salt + numericTokenSecret)
    "%04d".format(Math.abs(num)).substring(0, 4)

  }
}