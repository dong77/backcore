package com.coinport.coinex

class AccountView extends common.ExtendedView[AccountViewState] {
  override def processorId = "coinex_account_processor"
  var state = new AccountViewState()

  def receive = {
    case _ =>
  }
}

class AccountViewState {
}
