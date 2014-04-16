package com.coinport.coinex.integration

import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import scala.concurrent.duration._
import akka.actor.actorRef2Scala

// TODO(d): somehow the deposits are not saved into the right mongodb collection.
// TODO(d): complete the spec.
class DepositWithdrawIntegrationSpec extends IntegrationSpec(new Environment) {
  import env._
  "CoinexApp" must {
    "save and retrieve deposit requests" in {
      val deposit = Deposit(1, 10000, Rmb, 500000000L, TransferStatus.Pending)
      client ! DoRequestCashDeposit(deposit)
      val RequestCashDepositSucceeded(d) = receiveOne(4 seconds)
      d.status should be(TransferStatus.Pending)
      d.created shouldNot be(None)
      d.id should be(0X1000000000001L)
      Thread.sleep(1000)
    }
  }
}