package com.coinport.coinex.opendata

import com.mongodb.casbah.MongoDB
import com.coinport.coinex.data._
import com.coinport.coinex.common.PersistentId._
import Implicits._

class MarketUpdateEventExportView(val db: MongoDB) extends EventExportToMongoView {
  val pid = MARKET_UPDATE_PROCESSOR <<

  def shouldExport(event: AnyRef) = event match {
    case OrderCancelled => true
    case OrderSubmitted => true
    case _ => false
  }
}