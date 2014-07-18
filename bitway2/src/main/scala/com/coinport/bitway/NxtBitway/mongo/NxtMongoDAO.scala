/**
 * Created by chenxi on 7/16/14.
 */

package com.coinport.bitway.NxtBitway.mongo

import com.mongodb.casbah.Imports._
import com.coinport.bitway.NxtBitway.model.NxtAddressModel
import com.coinport.coinex.data.CryptoCurrencyAddressType

class NxtMongoDAO(collection: MongoCollection) {
  val ACCOUNT_ID = "_id"
  val ACCOUNT_RS = "rs"
  val SECRET = "s"
  val PUBLIC_KEY = "pk"
  val CREATED = "@c"
  val UPDATED = "@u"
  val TYPE = "t"

  def insertAddresses(nxts: Seq[NxtAddressModel]) = collection.insert(nxts.map(toBson): _*)

  def countAddress() = collection.count()

  def queryByTypes(aType: CryptoCurrencyAddressType) = collection.find(MongoDBObject(TYPE -> aType))


  private def toBson(nxt :NxtAddressModel): DBObject = {
    MongoDBObject(
      ACCOUNT_ID -> nxt.accountId,
      ACCOUNT_RS -> nxt.accountRS,
      SECRET -> nxt.secret,
      CREATED -> nxt.created,
      UPDATED -> nxt.updated,
      TYPE -> nxt.addressType.getValue()
    )
  }

  private def toClass(obj: MongoDBObject): DBObject = {
    NxtAddressModel(
      accountId = obj.getAs[String](ACCOUNT_ID).get,
      accountRS = obj.getAs[String](ACCOUNT_RS).get,
      secret = obj.getAs[String](SECRET).get,
      publicKey = obj.getAs[String](PUBLIC_KEY).get,
      addressType = CryptoCurrencyAddressType.get(obj.getAs[Int](TYPE).get).get,
      updated = obj.getAs[Long](UPDATED).get,
      created = obj.getAs[Long](CREATED).get
    )
  }
}
