/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.coinport.coinex.data.Fee

trait FeeMaker {
  def count[T](feeEvent: T): List[Fee]
}
