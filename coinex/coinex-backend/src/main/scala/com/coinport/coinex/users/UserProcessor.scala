/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.users

import akka.persistence.SnapshotOffer
import com.coinport.coinex.data._
import akka.actor._
import akka.persistence._
import com.coinport.coinex.common.ExtendedProcessor
import ErrorCode._
import akka.event.LoggingReceive

class UserProcessor(mailer: ActorRef, userManagerSecret: String) extends EventsourcedProcessor {
  override val processorId = "coinex_up"

  val manager = new UserManager(userManagerSecret)

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case Login(email, password) =>
      manager.checkLogin(email, password) match {
        case Left(error) => sender ! LoginFailed(error)
        case Right(profile) => sender ! LoginSucceeded(profile.id, profile.email)
      }

    case ValidatePasswordResetToken(token) =>
      manager().passwordResetTokenMap.get(token) match {
        case Some(id) => sender ! PasswordResetTokenValidationResult(manager().profileMap.get(id))
        case None => sender ! PasswordResetTokenValidationResult(None)
      }

    case m @ DoRegisterUser(userProfile, password) => persist(m)(updateState)
    case m @ DoRequestPasswordReset(email) => persist(m)(updateState)
    case m @ DoResetPassword(email, password, token) => persist(m)(updateState)

  }

  def updateState(event: Any) = event match {
    case DoRegisterUser(userProfile, password) =>
      manager.registerUser(userProfile, password, lastSequenceNr) match {
        case Left(error) =>
          sender ! RegisterUserFailed(error)
        case Right(profile) =>
          sender ! RegisterUserSucceeded(profile)

          mailer ! DoSendEmail(profile.email, EmailType.RegisterVerify, Map(
            "NAME" -> profile.realName.getOrElse(profile.email),
            "LANG" -> "CHINESE",
            "TOKEN" -> profile.verificationToken.get))
      }

    case DoRequestPasswordReset(email) =>
      manager.requestPasswordReset(email, lastSequenceNr) match {
        case Left(error) => sender ! RequestPasswordResetFailed(error)
        case Right(profile) =>
          sender ! RequestPasswordResetSucceeded(profile.id, profile.email, profile.passwordResetToken.get)

          mailer ! DoSendEmail(profile.email, EmailType.PasswordResetToken, Map(
            "NAME" -> profile.realName.getOrElse(profile.email),
            "LANG" -> "CHINESE",
            "TOKEN" -> profile.passwordResetToken.get))
      }

    case DoResetPassword(email, password, token) =>
      manager.resetPassword(email, password, token) match {
        case Left(error) => sender ! ResetPasswordFailed(error)
        case Right(profile) => sender ! ResetPasswordSucceeded(profile.id, profile.email)
      }
  }

}
