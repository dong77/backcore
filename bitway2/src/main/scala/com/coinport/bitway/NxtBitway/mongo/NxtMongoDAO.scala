/**
 * Created by chenxi on 7/16/14.
 */

package com.coinport.bitway.NxtBitway.mongo

import com.mongodb.casbah.Imports._
import com.coinport.bitway.NxtBitway.model._
import com.coinport.coinex.data.CryptoCurrencyAddressType

class NxtMongoDAO(collection: MongoCollection) {
  val ACCOUNT_ID = "_id"
  val ACCOUNT_RS = "rs"
  val SECRET = "s"
  val PUBLIC_KEY = "pk"
  val CREATED = "@c"
  val UPDATED = "@u"
  val TYPE = "t"

  def insertAddresses(nxts: Seq[NxtAddress]) = collection.insert(nxts.map(toBson): _*)

  def countAddress() = collection.count()

  def queryByTypes(aType: CryptoCurrencyAddressType) = collection.find(MongoDBObject(TYPE -> aType)).toSeq.map(toClass)

  def queryOneByTypes(aType: CryptoCurrencyAddressType) = collection.findOne(MongoDBObject(TYPE -> aType)).map(toClass)

  def queryByAccountIds(accountIds: Seq[String]) = collection.find(ACCOUNT_ID $in accountIds).map(toClass)

  def queryOneUser(id: String) = collection.findOne($or(ACCOUNT_ID -> id, ACCOUNT_RS -> id)).map(toClass)

  private def toBson(nxt :NxtAddress) = {
    MongoDBObject(
      ACCOUNT_ID -> nxt.accountId,
      ACCOUNT_RS -> nxt.accountRS,
      SECRET -> nxt.secret,
      CREATED -> nxt.created,
      UPDATED -> nxt.updated,
      TYPE -> nxt.addressType.getValue()
    )
  }

  private def toClass(obj: DBObject) = {
    NxtAddress(
      accountId = obj.getAs[String](ACCOUNT_ID).get,
      accountRS = obj.getAs[String](ACCOUNT_RS).get,
      secret = obj.getAs[String](SECRET).get,
      publicKey = obj.getAs[String](PUBLIC_KEY),
      addressType = CryptoCurrencyAddressType.get(obj.getAs[Int](TYPE).get).get,
      updated = obj.getAs[Long](UPDATED).get,
      created = obj.getAs[Long](CREATED).get
    )
  }
}
