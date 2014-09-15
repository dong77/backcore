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
    maintainedChainLength = 120,
    coldAddresses = List(
      CryptoAddress("1GbJtdiidFnbsGfuC5VtMKrRaoyrP2rRXk",
        message = Some("coinport"),
        signMessage = Some("H0YkvkM11/6tVddcu8dr+TEzhKjNuXVTn1ckaJJQOc0IVoAkCrhiA9CUFUWvTXRBL+DwLgPUrWi7gHnD+LBLztw="))
    ),
    hotColdTransferNumThreshold = 3E8.toLong,
    confirmNum = 1
  ),
  Ltc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List(
      CryptoAddress("LTaaHE4JpeMFuexYZi19wJxbWqcHAC4DFp",
        message = Some("coinport"),
        signMessage = Some("IOO7xXEp5RgrcPE+MWr+LJSQlwPOpCDAmuFbVz+1CMb5uFr8VJPaySsjxYXs65+rp8+torB7/G/DJwx4c/h3nzA="))
    ),
    hotColdTransfer = Some(HotColdTransferStrategy(0.6, 0.1)),
    hotColdTransferNumThreshold = 20E8.toLong,
    confirmNum = 4
  ),
  Doge -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List(
      CryptoAddress("D8mHXhuo9XFH5VKVWVWa25eCHbAPu3iGyp",
        message = Some("coinport"),
        signMessage = Some("H9gsmheo4pcjQzDr+aessL6u0DVwymAu2HynKNSn99j/RGvNAcW3Kjvc20ZqBYG5lGeEsLc3Be+kkHI9PSQAtfU="))
    ),
    hotColdTransfer = Some(HotColdTransferStrategy(0.6, 0.1)),
    hotColdTransferNumThreshold = 50000E8.toLong,
    confirmNum = 4
  ),
  Bc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List(
      CryptoAddress("B4h1qQkghc8gwNWqEfHNyVi4DvjnJhPCs1",
        message = Some("coinport"),
        signMessage = Some("IKPVXzuNDmIqov3MSQNUWB9eKEPaqFiifWJUsTxELDBcAo6131AdnVxNsGo5lP7IQ0Svo1rFXB55NGIAviea65I="))),
    hotColdTransfer = Some(HotColdTransferStrategy(0.6, 0.1)),
    hotColdTransferNumThreshold = 1000E8.toLong,
    confirmNum = 10
  ),
  Drk -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List(
      CryptoAddress("XjDSA4wfcigb9a13yoT4tQrS31dDuMzRp8",
        message = Some("coinport"),
        signMessage = Some("H9X8t4F4Hou4SwmvU+OCeEiV7avqPEFJv/Kj+1QllYeZGuxvn+hn9Zw1/x8EIXSU/wjIvs19OiqHR7nUlHsPRgk="))
    ),
    hotColdTransfer = Some(HotColdTransferStrategy(0.6, 0.1)),
    hotColdTransferNumThreshold = 30E8.toLong,
    confirmNum = 4
  ),
  Vrc -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List(
      CryptoAddress("VEb5y22HNyRvhiT28jGNjpRVxBE6x4FJyt",
        message = Some("coinport"),
        signMessage = Some("IL6UjDPgrzcDJ6RomadVdWiTFqcS5ck11qAiKYmP8bZiO8pl8WTptqFw6ymq8HTlx4n8u985kYRnWys/qOZ4Hgo="))
    ),
    hotColdTransfer = Some(HotColdTransferStrategy(0.6, 0.1)),
    hotColdTransferNumThreshold = 20E8.toLong,
    confirmNum = 4
  ),
  Zet -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    coldAddresses = List(
      CryptoAddress("ZKa4mZ2K9J9SJKFNGwFT55SjR3BzNd7LLP",
        message = Some("coinport"),
        signMessage = Some("IGnKrdkB1386bAQrsYzZ8ExC6Ro/OGU6DK8CwWK6ElhXSOQO3r8XFKTv87SW7/Qa96Jnr9W1W1WF0EMCn12UmQs="))
    ),
    hotColdTransfer = Some(HotColdTransferStrategy(0.6, 0.1)),
    hotColdTransferNumThreshold = 80E8.toLong,
    confirmNum = 10
  ),
  Btsx -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false,
    confirmNum = 50,
    userIdFromMemo = true,
    isDepositHot = true,
    enableFetchAddress = false
  ),
  Nxt -> BitwayConfig(
    ip = "bitway",
    port = 6379,
    maintainedChainLength = 120,
    enableHotColdTransfer = false,
    confirmNum = 5,
    hotColdTransferNumThreshold = 10E8.toLong
  )
))
