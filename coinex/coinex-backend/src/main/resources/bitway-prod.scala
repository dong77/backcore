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
    maintainedChainLength = 10,
    coldAddresses = List("1GbJtdiidFnbsGfuC5VtMKrRaoyrP2rRXk")
  ),
  Ltc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 20,
    coldAddresses = List("LTaaHE4JpeMFuexYZi19wJxbWqcHAC4DFp")
  ),
  Doge -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 60,
    coldAddresses = List("D8mHXhuo9XFH5VKVWVWa25eCHbAPu3iGyp")
  ),
  Drk -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 20,
    coldAddresses = List("XjDSA4wfcigb9a13yoT4tQrS31dDuMzRp8")
  ),
  Bc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List("B4h1qQkghc8gwNWqEfHNyVi4DvjnJhPCs1")
  )
))
