package com.coinport.coinex.common.mongo

import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.common._

class SimpleMongoCollectionSpec extends EmbeddedMongoForTestWithBF {
  //  step(embeddedMongoStartup())

  val jsonDeposits = new SimpleJsonMongoCollection[Deposit, Deposit.Immutable]() {
    val coll = database("deposits_json")
    def extractId(deposit: Deposit) = deposit.id
  }

  "SimpleJsonMongoCollection" must {
    "save and retrieve deposits" in {
      val deposit = Deposit(1, 2, Rmb, 123, TransferStatus.Pending)
      jsonDeposits.put(deposit)
      jsonDeposits.get(1) should be(Some(deposit))
      jsonDeposits.get(2) should be(None)
    }
  }

  val binaryDeposits = new SimpleBinaryMongoCollection[Deposit, Deposit.Immutable]() {
    val coll = database("deposits_binary")
    def extractId(deposit: Deposit) = deposit.id
  }

  "SimpleBinaryMongoCollection" must {
    "save and retrieve deposits" in {
      val deposit = Deposit(1, 2, Rmb, 123, TransferStatus.Pending)
      binaryDeposits.put(deposit)
      binaryDeposits.get(1) should be(Some(deposit))
      binaryDeposits.get(2) should be(None)
    }
  }
  //  step(embeddedMongoShutdown())
}
