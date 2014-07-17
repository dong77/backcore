/**
 * Created by chenxi on 7/16/14.
 */

package com.coinport.bitway.NxtBitway

import com.mongodb.casbah.Imports._
import com.coinport.bitway.NxtBitway.model.NxtAddressModel

class NxtMongoDAO(collection: MongoCollection) {
  val ACCOUNT_ID = "_id"
  val ACCOUNT_RS = "rs"
  val SECRET = "s"
  val PUBLIC_KEY = "pk"
  val CREATED = "@c"
  val UPDATED = "@u"

  def insertAddresses(nxts: Seq[NxtAddressModel]) = collection.insert(nxts.map(toBson): _*)

  def countAddress() = collection.count()

  private def toBson(nxt :NxtAddressModel): DBObject = {
    MongoDBObject(
      ACCOUNT_ID -> nxt.accountId,
      ACCOUNT_RS -> nxt.accountRS,
      SECRET -> nxt.secret,
      CREATED -> nxt.created,
      UPDATED -> nxt.updated
    )
  }
}
