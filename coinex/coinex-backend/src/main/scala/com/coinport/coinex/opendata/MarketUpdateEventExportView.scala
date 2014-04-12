package com.coinport.coinex.opendata

import com.mongodb.casbah.MongoDB
import com.coinport.coinex.data._

class MarketUpdateEventExportView(val db: MongoDB, val pid: String) extends EventExportToMongoView {
  def shouldExport(event: AnyRef) = event match {
    case OrderCancelled => true
    case OrderSubmitted => true
    case _ => false
  }
}