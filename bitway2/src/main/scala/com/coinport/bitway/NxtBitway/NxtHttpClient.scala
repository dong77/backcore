/**
 * Created by chenxi on 7/16/14.
 */

package com.coinport.bitway.NxtBitway

import dispatch._
import scala.util.parsing.json.JSON
import scala.concurrent.ExecutionContext.Implicits.global
import com.coinport.bitway.NxtBitway.model.NxtAddressModel

class NxtHttpClient(targetUrl: String) {
  val REQUEST_TYPE = "requestType"
  val GET_ACCOUNT_ID = "getAccountId"
  val SECRET_PHRASE = "secretPhrase"
  val ACCOUNT_ID = "accountId"
  val ACCOUNT_RS = "accountRS"

  def getMultiAddresses(secretList: Seq[String]): Seq[NxtAddressModel] = secretList.map(getSingleAddress)

  private def getSingleAddress(secret: String): NxtAddressModel = {
    val queryMap = Map(SECRET_PHRASE -> secret)
    val json = JSON.parseFull(getHttpResult(GET_ACCOUNT_ID, queryMap)).get.asInstanceOf[Map[String, String]]

    //todo(xichen): decode the secret
    NxtAddressModel(
      accountId = json.getOrElse(ACCOUNT_ID, ""),
      accountRS = json.getOrElse(ACCOUNT_RS, ""),
      secret = secret,
      publicKey = "",
      created = System.currentTimeMillis(),
      updated = System.currentTimeMillis())
  }

  private def getHttpResult(commend: String, map: Map[String, String]): String = {
    val rq: Req = url(targetUrl).subject.POST <<? (Map(REQUEST_TYPE -> commend) ++ map)
    val result = Http(rq)
    result().getResponseBody
  }
}
