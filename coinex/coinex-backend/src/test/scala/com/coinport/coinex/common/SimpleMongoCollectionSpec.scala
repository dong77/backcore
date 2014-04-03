package com.coinport.coinex.common

import org.specs2.mutable._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._

class SimpleMongoCollectionSpec extends Specification with EmbeddedMongoSupport {
  step(embeddedMongoStartup())

  val jsonDeposits = new SimpleJsonMongoCollection[Deposit, Deposit.Immutable]() {
    val coll = database("deposits_json")
    def extractId(deposit: Deposit) = deposit.id
  }

  "SimpleJsonMongoCollection" should {
    "save and retrieve deposits" in {
      val deposit = Deposit(1, 2, Rmb, 123, TransferStatus.Pending)
      jsonDeposits.put(deposit)
      jsonDeposits.get(1) mustEqual Some(deposit)
      jsonDeposits.get(2) mustEqual None
    }
  }

  val binaryDeposits = new SimpleBinaryMongoCollection[Deposit, Deposit.Immutable]() {
    val coll = database("deposits_binary")
    def extractId(deposit: Deposit) = deposit.id
  }

  "SimpleBinaryMongoCollection" should {
    "save and retrieve deposits" in {
      val deposit = Deposit(1, 2, Rmb, 123, TransferStatus.Pending)
      binaryDeposits.put(deposit)
      binaryDeposits.get(1) mustEqual Some(deposit)
      binaryDeposits.get(2) mustEqual None
    }
  }
  step(embeddedMongoShutdown())
}
