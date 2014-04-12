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

import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import ErrorCode._
import Implicits._

class UserProcessor(mailer: ActorRef, userManagerSecret: String)
    extends ExtendedProcessor with EventsourcedProcessor with ActorLogging {
  override val processorId = USER_PROCESSOR <<

  val googleAuthenticator = new GoogleAuthenticator
  val manager = new UserManager(googleAuthenticator, userManagerSecret)

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case m @ DoRegisterUser(userProfile, password) =>
      if (manager.isEmailRegistered(userProfile.email)) {
        sender ! RegisterUserFailed(EmailAlreadyRegistered)
      } else {
        val profile = manager.regulateProfile(userProfile, password, lastSequenceNr)
        sender ! RegisterUserSucceeded(profile)
        persist(DoRegisterUser(profile, password))(updateState)
        sendEmailVerificationEmail(profile)
      }

    case m @ DoUpdateUserProfile(userProfile) =>
      if (!manager.isEmailRegistered(userProfile.email)) {
        sender ! UpdateUserProfileFailed(EmailAlreadyRegistered)
      } else {
        val profile = manager.getUser(userProfile.email)
        sender ! UpdateUserProfileSucceeded(profile.get)
        persist(DoUpdateUserProfile(userProfile))(updateState)
      }

    case m @ DoRequestPasswordReset(email) =>
      manager.getUser(email) match {
        case None => sender ! RequestPasswordResetFailed(UserNotExist)
        case Some(profile) if profile.email != email =>
          sender ! RequestPasswordResetFailed(TokenNotUnique)
        case Some(profile) if profile.passwordResetToken.isDefined =>
          sender ! RequestPasswordResetSucceeded(profile.id, profile.email, profile.passwordResetToken.get)
          sendRequestPasswordResetEmail(profile)
        case Some(profile) if profile.passwordResetToken.isEmpty => persist(m)(updateState)
      }

    case m @ DoResetPassword(email, password, passwordResetToken) =>
      manager.getUser(email) match {
        case Some(user) if user.passwordResetToken == passwordResetToken =>
          sender ! ResetPasswordSucceeded(user.id, user.email)
          persist(m)(updateState)
        case Some(user) =>
          sender ! ResetPasswordFailed(TokenNotMatch)
        case None =>
          sender ! ResetPasswordFailed(UserNotExist)
      }
  }

  def updateState: Receive = {
    case DoRegisterUser(profile, _) => manager.registerUser(profile)
    case DoUpdateUserProfile(profile) => manager.updateUser(profile)

    case DoRequestPasswordReset(email) =>
      val profile = manager.requestPasswordReset(email, lastSequenceNr)
      sender ! RequestPasswordResetSucceeded(profile.id, profile.email, profile.passwordResetToken.get)
      if (!recoveryRunning) { sendRequestPasswordResetEmail(profile) }

    case DoResetPassword(email, password, token) =>
      manager.resetPassword(email, password, token)
  }

  def sendEmailVerificationEmail(profile: UserProfile) {
    mailer ! DoSendEmail(profile.email, EmailType.RegisterVerify, Map(
      "NAME" -> profile.realName.getOrElse(profile.email),
      "LANG" -> "CHINESE",
      "TOKEN" -> profile.verificationToken.get))
  }
  def sendRequestPasswordResetEmail(profile: UserProfile) {
    mailer ! DoSendEmail(profile.email, EmailType.PasswordResetToken, Map(
      "NAME" -> profile.realName.getOrElse(profile.email),
      "LANG" -> "CHINESE",
      "TOKEN" -> profile.passwordResetToken.get))
  }
}
