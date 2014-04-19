package com.coinport.coinex.opendata

import com.mongodb.casbah.MongoDB
import com.coinport.coinex.data._
import com.coinport.coinex.common.PersistentId._
import Implicits._

class AccountTransferEventExportView(db: MongoDB, snapshotIntervalSec: Int)
    extends EventExportToMongoView(db, ACCOUNT_TRANSFER_PROCESSOR <<, "account", snapshotIntervalSec) {
  def shouldExport(event: AnyRef) = event match {
    case m: AdminConfirmTransferSuccess => true
    case _ => false
  }
}