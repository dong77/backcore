/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

import com.coinport.coinex.common._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.bitway._
import Constants._
import Implicits._

BitwayConfigs(Map(
  Btc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    batchFetchAddressNum = 10,
    maintainedChainLength = 120,
    coldAddresses = List("1GbJtdiidFnbsGfuC5VtMKrRaoyrP2rRXk"),
    hotColdTransferNumThreshold = 3,
    confirmNum = 1
  ),
  Ltc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List("LTaaHE4JpeMFuexYZi19wJxbWqcHAC4DFp"),
    hotColdTransferNumThreshold = 20,
    confirmNum = 4
  ),
  Doge -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List("D8mHXhuo9XFH5VKVWVWa25eCHbAPu3iGyp"),
    hotColdTransferNumThreshold = 50000,
    confirmNum = 4
  ),
  Bc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List("B4h1qQkghc8gwNWqEfHNyVi4DvjnJhPCs1"),
    hotColdTransferNumThreshold = 1000,
    confirmNum = 10
  ),
  Drk -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List("XjDSA4wfcigb9a13yoT4tQrS31dDuMzRp8"),
    hotColdTransferNumThreshold = 30,
    confirmNum = 4
  ),
  Vrc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List("VEb5y22HNyRvhiT28jGNjpRVxBE6x4FJyt"),
    hotColdTransferNumThreshold = 20,
    confirmNum = 4
  ),
  Zet -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List("ZKa4mZ2K9J9SJKFNGwFT55SjR3BzNd7LLP"),
    hotColdTransferNumThreshold = 80,
    confirmNum = 10
  ),
  Btsx -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false,
    confirmNum = 10,
    userIdFromMemo = true,
    isDepositHot = true
  )
))
