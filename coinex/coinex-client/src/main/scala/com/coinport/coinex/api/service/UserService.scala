/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.service

import com.coinport.coinex.api.model._
import com.coinport.coinex.data._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

object UserService extends AkkaService {
  override def hashCode(): Int = super.hashCode()

  def register(user: User) = {
    val id = user.id
    val email = user.email
    val realName = user.realName
    val nationalId = user.nationalId
    val password = user.password

    val profile = UserProfile(
      id = -1L,
      email = email,
      realName = realName,
      nationalId = nationalId,
      passwordHash = Some(password),
      emailVerified = false,
      mobile = None,
      mobileVerified = false,
      passwordResetToken = None,
      verificationToken = None,
      loginToken = None,
      googleAuthenticatorSecret = None,
      UserStatus.Normal)

    val command = DoRegisterUser(profile, password)

    backend ? command map {
      case succeeded: RegisterUserSucceeded =>
        val returnProfile = succeeded.userProfile
        ApiResult(true, 0, returnProfile.id.toString, Some(returnProfile))
      case failed: RegisterUserFailed =>
        failed.error match {
          case ErrorCode.EmailAlreadyRegistered =>
            ApiResult(false, 1, "用户 " + email + " 已存在")
          case ErrorCode.MissingInformation =>
            ApiResult(false, 2, "缺少必填字段")
          case _ =>
            ApiResult(false, -1, failed.toString)
        }

      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def updateProfile(user: User) = {
    val id = user.id
    val email = user.email
    val realName = user.realName
    val nationalId = user.nationalId
    val mobile = user.mobile

    val profile = UserProfile(
      id = id.get,
      email = email,
      realName = realName,
      nationalId = nationalId,
      emailVerified = false,
      mobile = mobile,
      mobileVerified = true,
      status = UserStatus.Normal)

    val command = DoUpdateUserProfile(profile)
    backend ? command map {
      case succeeded: UpdateUserProfileSucceeded =>
        val returnProfile = succeeded.userProfile
        ApiResult(true, 0, returnProfile.id.toString, Some(returnProfile))
      case failed: UpdateUserProfileFailed =>
        ApiResult(false, -1, failed.toString)
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def login(user: User) = {
    val email = user.email
    val password = user.password

    val command = Login(email, password)

    backend ? command map {
      case succeeded: LoginSucceeded =>
        ApiResult(true, 0, "登录成功", Some(succeeded))
      case failed: LoginFailed =>
        failed.error match {
          case ErrorCode.PasswordNotMatch =>
            ApiResult(false, 1, "密码错误")
          case ErrorCode.UserNotExist =>
            ApiResult(false, 2, "用户 " + email + " 不存在")
          case _ =>
            ApiResult(false, -1, failed.toString)
        }
      case x =>
        ApiResult(false, -1, x.toString)
    }
  }

  def verifyEmail(token: String) = {
    val command = VerifyEmail(token)
    backend ? command map {
      case succeeded: VerifyEmailSucceeded =>
        ApiResult(true, 0, "注册邮箱验证成功", Some(succeeded))
      case failed: VerifyEmailFailed =>
        ApiResult(false, -1, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def requestPasswordReset(email: String) = {
    val command = DoRequestPasswordReset(email)
    backend ? command map {
      case succeeded: RequestPasswordResetSucceeded =>
        ApiResult(true, 0, "重置密码链接已发送，请查看注册邮箱", Some(succeeded))
      case failed: RequestPasswordResetFailed =>
        ApiResult(false, -1, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }

  def validatePasswordResetToken(token: String) = {
    val command = ValidatePasswordResetToken(token)
    backend ? command map {
      case result: PasswordResetTokenValidationResult =>
        result.userProfile match {
          case Some(profile) => ApiResult(true, 0, "", Some(profile))
          case None => ApiResult(false, -1, "")
        }
    }
  }

  def resetPassword(newPassword: String, token: String) = {
    val command = DoResetPassword(newPassword, token)
    backend ? command map {
      case succeeded: ResetPasswordSucceeded =>
        ApiResult(true, 0, "", Some(succeeded))
      case failed: ResetPasswordFailed =>
        ApiResult(false, -1, failed.toString)
      case e =>
        ApiResult(false, -1, e.toString)
    }
  }
}
