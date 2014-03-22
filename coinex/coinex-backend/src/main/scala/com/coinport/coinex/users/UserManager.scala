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
import scala.util.Random

class UserManager extends StateManager[UserState] {
  initWithDefaultState(UserState())

  def regulate(s: String) = s.trim.toLowerCase
  def emailToId(email: String) = Hash.murmur3(regulate(email))

  private def computePasswordHash(profile: UserProfile, password: String) = {
    Hash.sha256("%s~%s~%s~%s".format(
      profile.email, profile.realName, profile.nationalId, password))
  }

  def registerUser(profile: UserProfile, password: String): Either[RegisterationFailureReason, UserProfile] = {
    val email = regulate(profile.email)
    val id = emailToId(email)
    state.profileMap.get(id) match {
      case Some(_) => Left(RegisterationFailureReason.EmailAlreadyRegistered)
      case None =>
        val realName = regulate(profile.realName)
        val nationalId = regulate(profile.nationalId)
        val updatedProfile: UserProfile = profile.copy(
          id = id, email = email, realName = realName, nationalId = nationalId,
          emailVerified = false, status = UserStatus.Normal, randomSeed = id)

        val passwordHash = computePasswordHash(updatedProfile, password)
        val profileWithPassword = updatedProfile.copy(passwordHash = Some(passwordHash))
        state = state.addUserProfile(profileWithPassword)
        Right(profileWithPassword)
    }
  }

  def checkLogin(email: String, password: String): Either[LoginFailureReason, UserProfile] = {
    val id = emailToId(regulate(email))

    state.profileMap.get(id) match {
      case None => Left(LoginFailureReason.UserNotExist)
      case Some(profile) =>
        val passwordHash = computePasswordHash(profile, password)
        if (Some(passwordHash) == profile.passwordHash) Right(profile)
        else Left(LoginFailureReason.PasswordNotMatch)
    }
  }

  def requestPasswordReset(email: String): Either[RequestPasswordResetFailureReason, UserProfile] = {
    val id = emailToId(regulate(email))
    state.profileMap.get(id) match {
      case None => Left(RequestPasswordResetFailureReason.UserNotExist)
      case Some(profile) if profile.passwordResetToken.nonEmpty => Right(profile)
      case Some(profile) =>
        val random = new Random(profile.randomSeed)
        val randomSeed = random.nextLong
        val passwordResetToken = random.nextInt.toString

        val updatedProfile = profile.copy(randomSeed = randomSeed, passwordResetToken = Some(passwordResetToken))
        state = state.updateUserProfile(id)(_ => updatedProfile).addPasswordResetToken(passwordResetToken, id)
        Right(updatedProfile)
    }
  }

  // If passwordResetToken is None, we skip checking token matching
  def resetPassword(email: String, password: String, passwordResetToken: Option[String]): Either[ResetPasswordFailureReason, UserProfile] = {
    val id = emailToId(regulate(email))
    state.profileMap.get(id) match {
      case None => Left(ResetPasswordFailureReason.UserNotExist)

      case Some(profile) if passwordResetToken.isDefined || profile.passwordResetToken == passwordResetToken =>
        val randomSeed = new Random(profile.randomSeed).nextLong
        val passwordHash = computePasswordHash(profile, password)
        val updatedProfile = profile.copy(passwordHash = Some(passwordHash), randomSeed = randomSeed, passwordResetToken = None)
        state = state.updateUserProfile(id)(_ => updatedProfile)
        profile.passwordResetToken foreach { token => state = state.deletePasswordResetToken(token) }
        Right(updatedProfile)

      case _ => Left(ResetPasswordFailureReason.TokenNotMatch)
    }
  }
}