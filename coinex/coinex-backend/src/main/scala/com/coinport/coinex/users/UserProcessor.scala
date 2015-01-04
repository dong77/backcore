/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.users

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._
import com.coinport.coinex.util._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import ErrorCode._
import Implicits._
import java.security.SecureRandom

class UserProcessor(mailer: ActorRef, bitwayProcessors: collection.immutable.Map[Currency, ActorRef], secret: String)
    extends ExtendedProcessor with EventsourcedProcessor with ActorLogging {
  override val processorId = USER_PROCESSOR <<

  val googleAuthenticator = new GoogleAuthenticator
  val manager = new UserManager(googleAuthenticator, secret)

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case m @ DoRegisterUser(userProfile, password, referralParams) =>
      manager.getUser(userProfile.email) match {
        case Some(_) =>
          sender ! RegisterUserFailed(EmailAlreadyRegistered)
        case None =>
          val verificationToken = generateRandomHexToken(userProfile.email)
          val profile = manager.regulateProfile(userProfile, password, verificationToken, referralParams)
          persist(DoRegisterUser(profile, null, referralParams))(updateState)
          sender ! RegisterUserSucceeded(profile)
          sendEmailVerificationEmail(profile)
      }

    case m @ DoSendVerificationCodeEmail(email, code) =>
      manager.getUser(email) match {
        case Some(profile) =>
          sender ! SendVerificationCodeEmailSucceeded(profile.id, profile.email)
          sendVerificationCodeEmail(email, code)
        case None => sender ! SendVerificationCodeEmailFailed(UserNotExist)
      }

    case m @ DoResendVerifyEmail(email) =>
      manager.getUser(email) match {
        case Some(profile) =>
          sender ! ResendVerifyEmailSucceeded(profile.id, profile.email)
          sendEmailVerificationEmail(profile)
        case None =>
          sender ! ResendVerifyEmailFailed(UserNotExist)
      }

    case m @ DoUpdateUserProfile(userProfile) =>
      manager.getUser(userProfile.email) match {
        case Some(profile) =>
          val newProfile = profile.copy(
            realName = if (userProfile.realName.isDefined) userProfile.realName else profile.realName,
            nationalId = if (userProfile.nationalId.isDefined) userProfile.nationalId else profile.nationalId,
            mobile = if (userProfile.mobile.isDefined) userProfile.mobile else profile.mobile,
            mobileVerified = if (userProfile.mobile.isDefined) true else profile.mobileVerified,
            status = userProfile.status,
            depositAddresses = Some(profile.depositAddresses.getOrElse(Map.empty) ++ userProfile.depositAddresses.getOrElse(Map.empty)),
            withdrawalAddresses = Some(profile.withdrawalAddresses.getOrElse(Map.empty) ++ userProfile.withdrawalAddresses.getOrElse(Map.empty)),
            googleAuthenticatorSecret = userProfile.googleAuthenticatorSecret,
            securityPreference = userProfile.securityPreference
          )
          sender ! UpdateUserProfileSucceeded(newProfile)
          persist(DoUpdateUserProfile(newProfile))(updateState)
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

    case m @ DoChangePassword(email, oldPassword, newPassword) =>
      manager.checkLogin(email, oldPassword) match {
        case Left(error) => sender ! DoChangePasswordFailed(error)
        case Right(profile) =>
          persist(m)(updateState)
          sender ! DoChangePasswordSucceeded(profile.id, profile.email)
      }

    case m @ DoBindMobile(email, newMobile) =>
      manager.getUser(email) match {
        case Some(profile) =>
          val newProfile = profile.copy(mobile = Some(newMobile), mobileVerified = true)
          sender ! DoBindMobileSucceeded(profile.id, newMobile)
          persist(DoUpdateUserProfile(newProfile))(updateState)
        case None =>
          sender ! DoBindMobileFailed(UserNotExist)
      }

    case m @ DoSuspendUser(uid) =>
      manager.profileMap.get(uid) match {
        case Some(profile) =>
          val newProfile = profile.copy(status = UserStatus.Suspended)
          sender ! SuspendUserResult(Some(newProfile))
          persist(DoUpdateUserProfile(newProfile))(updateState)
        case _ =>
          sender ! SuspendUserResult(None)
      }

    case m @ DoResumeUser(uid) =>
      manager.profileMap.get(uid) match {
        case Some(profile) =>
          val newProfile = profile.copy(status = UserStatus.Normal)
          sender ! ResumeUserResult(Some(newProfile))
          persist(DoUpdateUserProfile(newProfile))(updateState)
        case _ =>
          sender ! ResumeUserResult(None)
      }

    case m @ VerifyEmail(token) =>
      manager.getUserWithVerificationToken(token) match {
        case Some(profile) if profile.verificationToken == Some(token) =>
          persist(m)(updateState)
          sender ! VerifyEmailSucceeded(profile.id, profile.email)
        case _ =>
          sender ! VerifyEmailFailed(TokenNotMatch)
      }

    case m @ DoVerifyRealName(userId, realName, location, identiType, idNumber) =>
      manager.profileMap.get(userId) match {
        case Some(profile) =>
          val newProfile = profile.copy(realName2 = Some(realName),
            location = Some(location), identificationType = Some(identiType),
            nationalId = Some(idNumber))
          sender ! VerifyRealNameSucceeded(newProfile)
          persist(DoUpdateUserProfile(newProfile))(updateState)
        case None =>
          sender ! VerifyRealNameFailed(UserNotExist)
      }

    case m @ DoAddBankCard(uid, bankCard) =>
      manager.profileMap.get(uid) match {
        case Some(profile) =>
          val newProfile = profile.copy(bankCards = Some(profile.bankCards.getOrElse(List.empty[BankCard]) :+ bankCard))
          sender ! AddBankCardSucceeded(uid, bankCard)
          persist(DoUpdateUserProfile(newProfile))(updateState)
        case None =>
          sender ! AddBankCardFailed(UserNotExist)
      }

    case m @ DoDeleteBankCard(uid, cardNumber) =>
      manager.profileMap.get(uid) match {
        case Some(profile) =>
          val newProfile = profile.copy(bankCards = profile.bankCards.map(_.filter(_.cardNumber != cardNumber)))
          sender ! DeleteBankCardSucceeded(uid, cardNumber)
          persist(DoUpdateUserProfile(newProfile))(updateState)
        case None =>
          sender ! DeleteBankCardFailed(UserNotExist)
      }

    case Login(email, password) =>
      manager.checkLogin(email, password) match {
        case Left(error) => sender ! LoginFailed(error)
        case Right(profile) => sender ! LoginSucceeded(profile)
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

    case m @ QueryProfile(uid, email) =>
      val profileOpt = if (uid.isDefined) manager.profileMap.get(uid.get) else manager.getUser(email.getOrElse(""))

      profileOpt match {
        case Some(profile) =>
          sender ! QueryProfileResult(Some(profile))
        case None =>
          sender ! QueryProfileResult(None)
      }
    case m @ CleanUserData(actions) =>
      if (actions.nonEmpty) {
        persist(m)(updateState)
        if (actions.contains(CleanActionType.CompleteNxtAddressIncomplete)) {
          bitwayProcessors(Currency.Nxt) ! QueryCryptoAddress(manager.getNxtDepositAddress)
        }
      }
    case m @ QueryCryptoAddressResult(cryptoAdds) =>
      if (cryptoAdds.nonEmpty) {
        persist(m)(updateState)
      }

  }

  def updateState: Receive = {
    case m: DoRegisterUser => manager.registerUser(m.userProfile)
    case DoUpdateUserProfile(profile) => manager.updateUser(profile)

    case DoRequestPasswordReset(email, token) =>
      manager.requestPasswordReset(email, token.get)
      if (recoveryFinished) sendRequestPasswordResetEmail(manager.getUser(email).get)

    case DoResetPassword(password, token) => manager.resetPassword(password, token)
    case DoChangePassword(email, oldPassword, newPassword) => manager.changePassword(email, newPassword)
    case VerifyEmail(token) => manager.verifyEmail(token)
    case CleanUserData(actions) => manager.cleanData(actions)
    case QueryCryptoAddressResult(cryptoAdds) => manager.updateNxtDepositAddresses(cryptoAdds)
  }

  private def sendVerificationCodeEmail(email: String, code: String) {
    log.info(s"(email verification code : $email, $code)")
    mailer ! DoSendEmail(email, EmailType.VerificationCode, Map("CODE" -> code))
  }

  private def sendEmailVerificationEmail(profile: UserProfile) {
    log.info(s"(register verification code : ${profile.email}, ${profile.verificationToken.getOrElse("")})")
    if (profile.verificationToken.isDefined)
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
    MHash.sha256Base32(email + rand.nextLong + hexTokenSecret).substring(0, 40)
}
