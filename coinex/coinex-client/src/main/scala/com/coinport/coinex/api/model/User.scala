/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

case class User(id: Option[Long], email: String, realName: Option[String] = None, password: String, nationalId: Option[String] = None)
