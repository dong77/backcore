/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway

import org.specs2.mutable._
import scala.collection.mutable.Set

import com.coinport.coinex.data._
import Implicits._
import Currency._

class BitwayManagerSpec extends Specification {
  import CryptoCurrencyAddressType._

  "BitwayManager" should {
    val bwm = new BitwayManager(Btc)
    "address accocate test" in {
      bwm.isDryUp mustEqual true
      bwm.faucetAddress(Unused, Set("d1", "d2", "d3", "d4", "d5", "d6"))
      bwm.isDryUp mustEqual false

      val d1 = bwm.allocateAddress
      val d1_ = bwm.allocateAddress
      d1 mustEqual d1_

      // bwm.addressAllocated(d1)
    }
  }
}
