/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.users

import akka.actor._
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import akka.persistence.SnapshotOffer
import akka.persistence._
import com.coinport.coinex.util._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import ErrorCode._
import Implicits._
import java.security.SecureRandom

class UserProcessor(mailer: ActorRef, secret: String)
    extends ExtendedProcessor with EventsourcedProcessor with ActorLogging {
  override val processorId = USER_PROCESSOR <<

  val googleAuthenticator = new GoogleAuthenticator
  val manager = new UserManager(googleAuthenticator, secret)

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case m @ DoRegisterUser(userProfile, password) =>
      manager.getUser(userProfile.email) match {
        case Some(_) =>
          sender ! RegisterUserFailed(EmailAlreadyRegistered)
        case None =>
          val verificationToken = generateRandomHexToken(userProfile.email)
          val profile = manager.regulateProfile(userProfile, password, verificationToken)
          persist(DoRegisterUser(profile, "" /* ignored */ ))(updateState)
          sender ! RegisterUserSucceeded(profile)
          sendEmailVerificationEmail(profile)
      }

    case m @ DoUpdateUserProfile(userProfile) =>
      manager.getUser(userProfile.email) match {
        case Some(profile) =>
          sender ! UpdateUserProfileSucceeded(profile)
          persist(DoUpdateUserProfile(userProfile))(updateState)
        case None =>
          sender ! UpdateUserProfileFailed(UserNotExist)
      }

    case m @ DoRequestPasswordReset(email, _) =>
      manager.getUser(email) match {

        case Some(profile) if profile.passwordResetToken.isDefined =>
          sender ! RequestPasswordResetSucceeded(profile.id, profile.email)
          sendRequestPasswordResetEmail(profile)

        case Some(profile) if profile.passwordResetToken.isEmpty =>
          persist(m.copy(passwordResetToken = Some(generateRandomHexToken(email))))(updateState)
          sender ! RequestPasswordResetSucceeded(profile.id, profile.email)
          sendRequestPasswordResetEmail(profile)

        case None => sender ! RequestPasswordResetFailed(UserNotExist)
      }

    case m @ DoResetPassword(_, token) =>
      manager.getUserWithPasswordResetToken(token) match {
        case Some(profile) if profile.passwordResetToken == Some(token) =>
          persist(m)(updateState)
          sender ! ResetPasswordSucceeded(profile.id, profile.email)
        case _ =>
          sender ! ResetPasswordFailed(TokenNotMatch)
      }

    case m @ VerifyEmail(token) =>
      manager.getUserWithPasswordResetToken(token) match {
        case Some(profile) if profile.verificationToken == Some(token) =>
          persist(m)(updateState)
          sender ! VerifyEmailSucceeded(profile.id, profile.email)
        case _ =>
          sender ! VerifyEmailFailed(TokenNotMatch)
      }

    case Login(email, password) =>
      manager.checkLogin(email, password) match {
        case Left(error) => sender ! LoginFailed(error)
        case Right(profile) => sender ! LoginSucceeded(profile.id, profile.email)
      }

    case ValidatePasswordResetToken(token) =>
      manager.passwordResetTokenMap.get(token) match {
        case Some(id) => sender ! PasswordResetTokenValidationResult(manager.profileMap.get(id))
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

  def updateState: Receive = {
    case DoRegisterUser(profile, _) => manager.registerUser(profile)
    case DoUpdateUserProfile(profile) => manager.updateUser(profile)
    case DoRequestPasswordReset(email, token) => manager.requestPasswordReset(email, token.get)
    case DoResetPassword(password, token) => manager.resetPassword(password, token)
    case VerifyEmail(token) => manager.verifyEmail(token)
  }

  private def sendEmailVerificationEmail(profile: UserProfile) {
    mailer ! DoSendEmail(profile.email, EmailType.RegisterVerify, Map(
      "NAME" -> profile.realName.getOrElse(profile.email),
      "LANG" -> "CHINESE",
      "TOKEN" -> profile.verificationToken.get))
  }

  private def sendRequestPasswordResetEmail(profile: UserProfile) {
    mailer ! DoSendEmail(profile.email, EmailType.PasswordResetToken, Map(
      "NAME" -> profile.realName.getOrElse(profile.email),
      "LANG" -> "CHINESE",
      "TOKEN" -> profile.passwordResetToken.get))
  }

  private val rand = SecureRandom.getInstance("SHA1PRNG", "SUN")
  private val hexTokenSecret = MHash.sha256Base64(secret + "hexTokenSecret")

  private def generateRandomHexToken(email: String) =
    MHash.sha256Base32(email + rand.nextLong + hexTokenSecret).substring(0, 77)
}
