/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import org.specs2.mutable._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Set

import com.coinport.coinex.data._
import Implicits._
import Currency._

class BitwayManagerSpec extends Specification {
  import CryptoCurrencyAddressType._

  "BitwayManager" should {
    "address accocate test" in {
      val bwm = new BitwayManager(Btc)

      bwm.getSupportedCurrency mustEqual Btc

      bwm.isDryUp mustEqual true
      bwm.faucetAddress(Unused, Set("d1", "d2", "d3", "d4", "d5", "d6"))
      bwm.isDryUp mustEqual false

      val d1 = bwm.allocateAddress
      val d1_ = bwm.allocateAddress
      d1 mustEqual d1_
      d1._2 mustEqual false

      bwm.addressAllocated(d1._1.get)
      val d2 = bwm.allocateAddress
      d1 mustNotEqual d2
      d2._2 mustEqual false
      bwm.addressAllocated(d2._1.get)
      val d3 = bwm.allocateAddress
      d3._2 mustEqual false
      bwm.addressAllocated(d3._1.get)
      val d4 = bwm.allocateAddress
      d4._2 mustEqual true
      bwm.addressAllocated(d4._1.get)
      val d5 = bwm.allocateAddress
      d5._1 mustNotEqual None
      d5._2 mustEqual true
      bwm.addressAllocated(d5._1.get)
      val d6 = bwm.allocateAddress
      d6._1 mustNotEqual None
      d6._2 mustEqual true
      bwm.addressAllocated(d6._1.get)
      val none = bwm.allocateAddress
      none mustEqual (None, true)
      val noneAgain = bwm.allocateAddress
      noneAgain mustEqual (None, true)
    }

    "get tx type test" in {
      val bwm = new BitwayManager(Btc)
      bwm.faucetAddress(Unused, Set("d1", "d2", "d3", "d4", "d5", "d6"))
      1 mustEqual 1
    }

    "block chain test" in {
      val bwm = new BitwayManager(Btc)
      bwm.getBlockIndexes mustEqual Some(ArrayBuffer.empty[BlockIndex])
      bwm.getCurrentBlockIndex mustEqual None
      // getBlockContinuity
    }

    "tx generation test" in {
      // completeCryptoCurrencyTransaction
      // extractTxsFromBlocks
      1 mustEqual 1
    }
  }
}
