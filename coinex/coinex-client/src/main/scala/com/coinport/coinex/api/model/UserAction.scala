/**
 * Copyright (C) 2014 Coinport Inc.
 */

package com.coinport.coinex.api.model

import com.coinport.coinex.data.UserActionType

case class UserActionPojo(id: Long,
  userId: Long,
  timestamp: Long,
  actionType: UserActionType,
  ip: Option[String] = None,
  location: Option[String] = None)
