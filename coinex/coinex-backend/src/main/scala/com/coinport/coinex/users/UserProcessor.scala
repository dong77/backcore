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
import RegisterationFailureReason._
import akka.event.LoggingReceive

class UserProcessor(mailer: ActorRef) extends ExtendedProcessor {
  override val processorId = "coinex_up"

  val manager = new UserManager()

  def receive = LoggingReceive {
    // ------------------------------------------------------------------------------------------------
    // Snapshots
    // TODO(c) add global flag to indicate if is snapshoting
    case TakeSnapshotNow => saveSnapshot(manager())

    case SaveSnapshotSuccess(metadata) =>

    case SaveSnapshotFailure(metadata, reason) =>

    case SnapshotOffer(meta, snapshot) =>
      log.info("Loaded snapshot {}", meta)
      manager.reset(snapshot.asInstanceOf[UserState])

    case DebugDump =>
      log.info("state: {}", manager())

    // ------------------------------------------------------------------------------------------------
    // Non-persistent requests
    case Login(email, password) =>
      manager.checkLogin(email, password) match {
        case Left(reason) => sender ! LoginFailed(reason)
        case Right(profile) => sender ! LoginSucceeded(profile.id, profile.email)
      }

    case ValidatePasswordResetToken(token) =>
      manager().passwordResetTokenMap.get(token) match {
        case Some(id) => sender ! ValidatePasswordResetTokenResult(manager().profileMap.get(id))
        case None => ValidatePasswordResetTokenResult(None)
      }

    // ------------------------------------------------------------------------------------------------
    // Commands
    case p @ Persistent(DoRegisterUser(userProfile, password), seqNr) =>
      manager.registerUser(userProfile, password, seqNr) match {
        case Left(reason) =>
          sender ! RegisterUserFailed(reason, None)
        case Right(profile) =>
          sender ! RegisterUserSucceeded(profile)

          mailer ! SendMailRequest(profile.email, EmailType.RegisterVerify, Map(
            "NAME" -> profile.realName.getOrElse(profile.email),
            "LANG" -> "CHINESE",
            "TOKEN" -> profile.verificationToken.get))
      }

    case p @ Persistent(DoRequestPasswordReset(email), seqNr) =>
      manager.requestPasswordReset(email, seqNr) match {
        case Left(reason) => sender ! RequestPasswordResetFailed(reason)
        case Right(profile) =>
          sender ! RequestPasswordResetSucceeded(profile.id, profile.email, profile.passwordResetToken.get)

          mailer ! SendMailRequest(profile.email, EmailType.PasswordResetToken, Map(
            "NAME" -> profile.realName.getOrElse(profile.email),
            "LANG" -> "CHINESE",
            "TOKEN" -> profile.passwordResetToken.get))
      }

    case p @ Persistent(DoResetPassword(email, password, token), _) =>
      manager.resetPassword(email, password, token) match {
        case Left(reason) => sender ! ResetPasswordFailed(reason)
        case Right(profile) => sender ! ResetPasswordSucceeded(profile.id, profile.email)
      }
  }
}
