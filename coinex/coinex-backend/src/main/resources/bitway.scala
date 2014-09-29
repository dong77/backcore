/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

import com.coinport.coinex.common._
import com.coinport.coinex.data._
import com.coinport.coinex.data.CryptoAddress
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.bitway._
import Constants._
import Implicits._

BitwayConfigs(Map(
  Btc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    batchFetchAddressNum = 10,
    maintainedChainLength = 10,
    coldAddresses = List(CryptoAddress("morrnAssmf3LP2UkXST7i3UpKcUbBQX4GU")),
    enableHotColdTransfer = false,
    usersToInnerNumThreshold = 1E7.toLong
  ),
  Ltc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 20,
    enableHotColdTransfer = false,
    usersToInnerNumThreshold = 2E8.toLong
  ),
  Doge -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 20,
    coldAddresses = List(CryptoAddress(
      "nUmdT61hFz2MWeHdLbGDjDmdVKVRUiuhnu")),
    enableHotColdTransfer = false,
    usersToInnerNumThreshold = 100E8.toLong
  ),
  Bc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 20,
    enableHotColdTransfer = false
  ),
  Drk -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 20,
    enableHotColdTransfer = false
  ),
  Vrc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false
  ),
  Zet -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false
  ),
  Btsx -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false,
    confirmNum = 10,
    userIdFromMemo = true,
    isDepositHot = true,
    checkDepositAccountName = true,
    enableFetchAddress = false,
    enableUsersToInnerTransfer = false
  ),
  Nxt -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false,
    confirmNum = 10,
    enableUsersToInnerTransfer = false
  ),
  Xrp -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false,
    confirmNum = 1,
    userIdFromMemo = true,
    isDepositHot = true,
    enableFetchAddress = false,
    enableUsersToInnerTransfer = false
  )
))
