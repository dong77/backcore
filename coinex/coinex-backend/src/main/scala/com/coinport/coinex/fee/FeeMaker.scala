/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.coinport.coinex.data.Fee

trait FeeMaker[T] {
  def tipping(feeEvent: T): (T, List[Fee])
}
