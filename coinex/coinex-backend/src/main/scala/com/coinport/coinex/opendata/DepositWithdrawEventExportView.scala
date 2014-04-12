package com.coinport.coinex.opendata

import com.mongodb.casbah.MongoDB
import com.coinport.coinex.data._
import com.coinport.coinex.common.PersistentId._
import Implicits._

class DepositWithdrawEventExportView(val db: MongoDB) extends EventExportToMongoView {
  val pid = DEPOSIT_WITHDRAW_PROCESSOR <<

  def shouldExport(event: AnyRef) = event match {
    case AdminConfirmCashDepositSuccess => true
    case AdminConfirmCashWithdrawalSuccess => true
    case _ => false
  }
}