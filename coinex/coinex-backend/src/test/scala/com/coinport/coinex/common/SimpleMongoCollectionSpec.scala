package com.coinport.coinex.common

import org.specs2.mutable._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.serializers._
import com.mongodb.casbah._

// TODO(xi): use in-memory mongod
class SimpleMongoCollectionSpec extends Specification {
  val uri = MongoURI("mongodb://localhost:27017/test2")
  val mongo = MongoConnection(uri)
  val database = mongo(uri.database.getOrElse("coinex_export"))

  val jsonDeposits = new SimpleJsonMongoCollection[Deposit, Deposit.Immutable]() {
    val coll = database("deposits1")
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
    val coll = database("deposits2")
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
}
