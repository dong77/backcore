package com.coinport.coinex

import akka.persistence.SnapshotOffer

class AccountProcessor extends common.ExtendedProcessor {
  override val processorId = "coinex_ap"

  override val receiveMessage: Receive = {
    case SnapshotOffer(_, _) =>
  }
}
