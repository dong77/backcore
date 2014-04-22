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
}
