package com.coinport.coinex.opendata

import com.mongodb.casbah.MongoDB
import com.coinport.coinex.data._

class DepositWithdrawEventExportView(val db: MongoDB, val pid: String) extends EventExportToMongoView {
  def shouldExport(event: AnyRef) = event match {
    case AdminConfirmCashDepositSuccess => true
    case AdminConfirmCashWithdrawalSuccess => true
    case _ => false
  }
}