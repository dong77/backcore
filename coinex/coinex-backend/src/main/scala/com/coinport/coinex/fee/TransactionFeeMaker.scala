/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.fee

import com.coinport.coinex.data._
import com.coinport.coinex.common.Constants.COINPORT_UID
import com.typesafe.config.Config

// TODO(c): consider the type of the user
class TransactionFeeMaker(config: Config) extends FeeMaker[Transaction] {
  override def tipping(serviceTakeItem: Transaction): (Transaction, List[Fee]) = {
    (null, null)
  }
}
