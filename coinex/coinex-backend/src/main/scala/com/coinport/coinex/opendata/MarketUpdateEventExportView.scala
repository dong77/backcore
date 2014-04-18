package com.coinport.coinex.opendata

import com.mongodb.casbah.MongoDB
import com.coinport.coinex.data._
import com.coinport.coinex.common.PersistentId._
import Implicits._

class MarketUpdateEventExportView(db: MongoDB, snapshotIntervalSec: Int)
    extends EventExportToMongoView(db, MARKET_UPDATE_PROCESSOR <<, "market", snapshotIntervalSec) {

  def shouldExport(event: AnyRef) = event match {
    case m: OrderCancelled => true
    case m: OrderSubmitted => true
    case _ => false
  }
}