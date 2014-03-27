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

class UserManager extends StateManager[UserState] {
  initWithDefaultState(UserState())

  val SALT_SUGER = -8431060239719927652L
  val HEX_TOKEN_SUGER = 91735161L
  val NUMERIC_TOKEN_SUGER = 49838196L

  def regulate(s: String) = s.trim.toLowerCase
  def emailToId(email: String) = Hash.murmur3(regulate(email))

  private def computePasswordHash(email: String, password: String, salt: Long) = {
    BaseEncoding.base64.encode(Hash.sha256("%s\n%s\n%d".format(email, password, salt)))
  }

  def registerUser(profile: UserProfile, password: String, seed: Long): Either[RegisterationFailureReason, UserProfile] = {
    val email = regulate(profile.email)
    val id = emailToId(email)
    val salt = seed ^ SALT_SUGER
    state.profileMap.get(id) match {
      case Some(_) => Left(RegisterationFailureReason.EmailAlreadyRegistered)
      case None =>
        val verificationToken = newHexToken(seed, email)
        val updatedProfile: UserProfile = profile.copy(
          id = id,
          email = email,
          emailVerified = false,
          passwordResetToken = None,
          verificationToken = Some(verificationToken),
          loginToken = None,
          status = UserStatus.Normal,
          salt = Some(salt))

        val passwordHash = computePasswordHash(email, password, salt)
        val profileWithPassword = updatedProfile.copy(passwordHash = Some(passwordHash))

        state = state.addUserProfile(profileWithPassword).addVerificationToken(verificationToken, id)
        Right(profileWithPassword)
    }
  }

  def checkLogin(email: String, password: String): Either[LoginFailureReason, UserProfile] = {
    val id = emailToId(regulate(email))

    state.profileMap.get(id) match {
      case None => Left(LoginFailureReason.UserNotExist)
      case Some(profile) =>
        val passwordHash = computePasswordHash(profile.email, password, profile.salt.get)
        if (Some(passwordHash) == profile.passwordHash) Right(profile)
        else Left(LoginFailureReason.PasswordNotMatch)
    }
  }

  def requestPasswordReset(email: String, seed: Long): Either[RequestPasswordResetFailureReason, UserProfile] = {
    val e = regulate(email)
    val id = emailToId(e)
    state.profileMap.get(id) match {
      case None => Left(RequestPasswordResetFailureReason.UserNotExist)
      case Some(profile) if profile.email != e => Left(RequestPasswordResetFailureReason.TokenNotUnique)
      case Some(profile) if profile.passwordResetToken.isDefined => Right(profile)
      case Some(profile) if profile.passwordResetToken.isEmpty =>

        val token = newHexToken(seed, e)
        val updatedProfile = profile.copy(passwordResetToken = Some(token))
        state = state.updateUserProfile(id)(_ => updatedProfile).addPasswordResetToken(token, id)
        Right(updatedProfile)
    }
  }

  def resetPassword(email: String, password: String, passwordResetToken: Option[String]): Either[ResetPasswordFailureReason, UserProfile] = {
    val id = emailToId(regulate(email))
    state.profileMap.get(id) match {
      case None => Left(ResetPasswordFailureReason.UserNotExist)

      case Some(profile) if profile.passwordResetToken == passwordResetToken =>
        profile.passwordResetToken foreach { token => state = state.deletePasswordResetToken(token) }
        val passwordHash = computePasswordHash(profile.email, password, profile.salt.get)
        val updatedProfile = profile.copy(passwordHash = Some(passwordHash), passwordResetToken = None)
        state = state.updateUserProfile(id)(_ => updatedProfile)
        Right(updatedProfile)

      case _ => Left(ResetPasswordFailureReason.TokenNotMatch)
    }
  }

  private def newHexToken(seed: Long, email: String): String = {
    val rand = new scala.util.Random((seed + HEX_TOKEN_SUGER) ^ Hash.murmur3(email))
    rand.nextLong.toHexString + rand.nextLong.toHexString
  }

  private def newNumericToken(seed: Long, email: String): String = {
    val rand = new scala.util.Random((seed + NUMERIC_TOKEN_SUGER) ^ Hash.murmur3(email))
    Math.abs((rand.nextLong % 1000000)).toString
  }
}