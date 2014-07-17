/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.bitway.NxtBitway

import com.coinport.coinex.data.Currency

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
  hotColdTransferInterval: Long = 24 * 3600 * 1000L)

final case class BitwayConfigs(
  configs: Map[Currency, BitwayConfig] = Map.empty)
