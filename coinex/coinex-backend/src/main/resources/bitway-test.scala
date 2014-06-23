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
    maintainedChainLength = 10
  ),
  Dog -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 20,
    coldAddresses = List(
      "nUmdT61hFz2MWeHdLbGDjDmdVKVRUiuhnu",
      "nmC5w3P33fqeKX8mcbHMFJzcurrkwZBZsK",
      "nkWbaspLDKqt4q8c7N8Eizzom27tJffgAX",
      "nhS7fCAjLpq1X5udwNZu51HpouY3m5PeT4")
  )
))
