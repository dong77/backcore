/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import com.coinport.coinex.data.Currency
import scala.concurrent.duration._

final case class HotColdTransferStrategy(high: Double, low: Double)

final case class BitwayConfig(
  ip: String = "bitway",
  port: Int = 6379,
  batchFetchAddressNum: Int = 100,
  requestChannelPrefix: String = "creq_",
  responseChannelPrefix: String = "cres_",
  maintainedChainLength: Int = 20,
  coldAddresses: List[String] = Nil,
  hotColdTransfer: Option[HotColdTransferStrategy] = Some(HotColdTransferStrategy(0.2, 0.1)),
  enableHotColdTransfer: Boolean = true,
  hotColdTransferNumThreshold: Long = 20L,
  hot2ColdTransferInterval: FiniteDuration = 5 * 60 seconds,
  hot2ColdTransferIntervalLarge: FiniteDuration = 3600 seconds,
  cold2HotTransferInterval: FiniteDuration = 3600 seconds,
  confirmNum: Int = 1)

final case class BitwayConfigs(
  configs: Map[Currency, BitwayConfig] = Map.empty)
