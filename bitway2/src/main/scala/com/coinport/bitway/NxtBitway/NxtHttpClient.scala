/**
 * Created by chenxi on 7/16/14.
 */

package com.coinport.bitway.NxtBitway

import dispatch._
import scala.util.parsing.json.JSON
import scala.concurrent.ExecutionContext.Implicits.global

class NxtHttpClient(targetUrl: String) {
  val REQUEST_TYPE = "requestType"
  val GET_ACCOUNT_ID = "getAccountId"
  val SECRET_PHRASE = "secretPhrase"

  def getMultiAddresses(secretList: Seq[String]): Seq[NxtAddressModel] = secretList.map(getSingleAddress)

  private def getSingleAddress(secret: String): NxtAddressModel = {
    val queryMap = Map(SECRET_PHRASE -> secret)
    val json = JSON.parseFull(getHttpResult(GET_ACCOUNT_ID, queryMap))

    NxtAddressModel(0, null, null, null, System.currentTimeMillis(), System.currentTimeMillis())
  }


  private def getHttpResult(commend: String, map: Map[String, String]): String = {
    val rq: Req = url(targetUrl) <<? (Map(REQUEST_TYPE -> commend) ++ map)
    val result = Http(rq)
    result().getResponseBody
  }
}
