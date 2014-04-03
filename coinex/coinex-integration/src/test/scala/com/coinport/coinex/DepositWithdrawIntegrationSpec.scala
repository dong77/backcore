package com.coinport.coinex

import com.coinport.coinex.data._
import Currency._

import scala.concurrent.duration._

// TODO(d): somehow the depoists are not saved into the right mongodb collectino.
// TODO(d): compelete the spec.
class DepositWithdrawIntegrationSpec extends IntegrationTest(new Environment) {
  import env._
  "CoinexApp" must {
    "save and retrieve deposit requests" in {
      val deposit = Deposit(1, 10000, Rmb, 500000000L, TransferStatus.Pending)
      client ! DoRequestCashDeposit(deposit)
      val RequestCashDepositSucceeded(d) = receiveOne(4 seconds)
      d.status shouldEqual TransferStatus.Pending
      d.created shouldNot (be(None))
      d.id shouldBe 0L
      Thread.sleep(1000)
    }
  }
}