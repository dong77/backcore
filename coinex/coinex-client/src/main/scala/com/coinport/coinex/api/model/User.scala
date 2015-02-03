/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

import com.coinport.coinex.data.Currency
import com.coinport.coinex.data.UserStatus

case class User(id: Long,
  email: String,
  realName: Option[String] = None,
  password: String,
  nationalId: Option[String] = None,
  mobile: Option[String] = None,
  depositAddress: Option[Map[Currency, String]] = None,
  withdrawalAddress: Option[Map[Currency, String]] = None,
  referedToken: Option[String] = None,
  status: UserStatus = UserStatus.Normal,
  googleAuthenticatorSecret: Option[String] = None,
  securityPreference: Option[String] = None,
  realName2: Option[String] = None)

case class ApiV2Profile(
  uid: Long,
  email: String,
  name: Option[String],
  mobile: Option[String],
  apiTokenPairs: Seq[Seq[Option[String]]],
  emailVerified: Boolean,
  mobileVerified: Boolean,
  googleAuthEnabled: Boolean)

