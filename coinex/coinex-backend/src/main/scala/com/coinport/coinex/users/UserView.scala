/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.users

import akka.event.LoggingReceive
import com.coinport.coinex.common.ExtendedView
import akka.persistence.Persistent
import com.coinport.coinex.data._

class UserView(userManagerSecret: String) extends ExtendedView {
  override val processorId = "coinex_up"
  override val viewId = "user_view"

  val googleAuthenticator = new GoogleAuthenticator
  val manager = new UserManager(googleAuthenticator, userManagerSecret)

  def receive = LoggingReceive {
    case Persistent(m, seq) => updateState(m)

    case Login(email, password) =>
      manager.checkLogin(email, password) match {
        case Left(error) => sender ! LoginFailed(error)
        case Right(profile) => sender ! LoginSucceeded(profile.id, profile.email)
      }

    case ValidatePasswordResetToken(token) =>
      manager.passwordResetTokenMap.get(token) match {
        case Some(id) => sender ! PasswordResetTokenValidationResult(manager().profileMap.get(id))
        case None => sender ! PasswordResetTokenValidationResult(None)
      }

    case VerifyGoogleAuthCode(email, code) =>
      manager.getUser(email) match {
        case Some(profile) if profile.googleAuthenticatorSecret.isDefined =>
          val secret = profile.googleAuthenticatorSecret.get
          val timeIndex = googleAuthenticator.getTimeIndex()
          if (googleAuthenticator.verifyCode(secret, code, timeIndex, 1)) GoogleAuthCodeVerificationResult(Some(profile))
          else GoogleAuthCodeVerificationResult(None)
        case _ => GoogleAuthCodeVerificationResult(None)
      }
  }

  def updateState(event: Any) = event match {
    case DoRegisterUser(profile, _) => manager.registerUser(profile)
    case DoUpdateUserProfile(profile) => manager.updateUser(profile)
    case DoRequestPasswordReset(email) => manager.requestPasswordReset(email, lastSequenceNr)
    case DoResetPassword(email, password, token) => manager.resetPassword(email, password, token)
  }
}