/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.bitway_client

import com.coinport.coinex.common.Manager
import com.coinport.coinex.data._

class BitwayManager extends Manager[TBitwayState] {

  def getSnapshot = TBitwayState();

  def loadSnapshot(s: TBitwayState) {
  }

}
