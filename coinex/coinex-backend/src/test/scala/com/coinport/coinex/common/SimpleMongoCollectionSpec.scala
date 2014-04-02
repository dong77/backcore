package com.coinport.coinex.common

import org.specs2.mutable._
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency._
import com.coinport.coinex.serializers._
import com.mongodb.casbah._

// TODO(d): use in-memory mongod
class SimpleMongoCollectionSpec extends Specification {

  val deposits = new SimpleMongoCollection[Deposit, Deposit.Immutable]() {
    lazy val uri = MongoURI("mongodb://localhost:27017/test2")
    lazy val mongo = MongoConnection(uri)
    lazy val database = mongo(uri.database.getOrElse("coinex_export"))
    val coll = database("deposits")
    def extractId(deposit: Deposit) = deposit.id
  }

  "DepositWithdralProcessing" should {
    "save and retrieve deposits" in {
      val deposit = Deposit(1, 2, Rmb, 123, TransferStatus.Pending)
      deposits.put(deposit)
      deposits.get(1) mustEqual Some(deposit)
      deposits.get(2) mustEqual None
    }
  }
}
