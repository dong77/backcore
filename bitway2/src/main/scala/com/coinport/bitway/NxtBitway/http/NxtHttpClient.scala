/**
 * Created by chenxi on 7/16/14.
 */

package com.coinport.bitway.NxtBitway.http

import dispatch._
import scala.util.parsing.json.JSON
import scala.concurrent.ExecutionContext.Implicits.global
import com.coinport.bitway.NxtBitway.model._
import com.coinport.coinex.data.CryptoCurrencyAddressType
import dispatch.Req

class NxtHttpClient(targetUrl: String) {
  def getMultiAddresses(secretList: Seq[String], addType: CryptoCurrencyAddressType) =
    secretList.map(s => getSingleAddress(s, addType))

  def getBlockChainStatus() = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getBlockchainStatus", queryMap)).get.asInstanceOf[Map[String, String]]

    NxtBlockStatus(
      timestamp = json.getOrElse("time", "0").toInt,
      lastBlockHeight = json.getOrElse("lastBlockchainFeederHeight", "0").toLong,
      lastBlockId =  json.getOrElse("lastBlock", "0")
    )
  }

  def getBlock(blockId: String) = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getBlock", queryMap)).get.asInstanceOf[Map[String, String]]

    NxtBlock(
      transactionIds = getIds(json.getOrElse("lastBlock", "[]")),
      nextBlock = json.getOrElse("lastBlock", "0"),
      previousBlock = json.getOrElse("previousBlock", "0"),
      timestamp = json.getOrElse("time", "0").toInt,
      height = json.getOrElse("height", "0").toLong
    )
  }

  def getTransaction(transactionId: String) = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getTransaction", queryMap)).get.asInstanceOf[Map[String, String]]

    NxtTransaction(
      transactionId = json.getOrElse("transaction", "0"),
      senderId = json.getOrElse("sender", "0"),
      senderRS = json.getOrElse("senderRS", "0"),
      recipientId = json.getOrElse("recipient", "0"),
      recipientRS = json.getOrElse("recipientRS", "0"),
      timestamp = json.getOrElse("timestamp", "0").toInt,
      blockId = json.getOrElse("block", "0"),
      height = json.getOrElse("height", "0").toInt,
      deadline = json.getOrElse("deadline", "0").toInt,
      subtype = json.getOrElse("subtype", "0").toInt,
      confirms  = json.getOrElse("confirmations", "0").toInt,
      amount = json.getOrElse("amountNQT", "0")
    )
  }

  def getUnconfirmedTrancationIds() = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getUnconfirmedTransactionIds", queryMap)).get.asInstanceOf[Map[String, String]]

    getIds(json.getOrElse("unconfirmedTransactionIds", "[]"))
  }

  private def getSingleAddress(secret: String, addType: CryptoCurrencyAddressType): NxtAddress = {
    val queryMap = Map("secretPhrase" -> secret)
    val json = JSON.parseFull(getHttpResult("getAccountId", queryMap)).get.asInstanceOf[Map[String, String]]

    //todo(xichen): decode the secret
    NxtAddress(
      accountId = json.getOrElse("accountId", ""),
      accountRS = json.getOrElse("accountRS", ""),
      secret = secret,
      publicKey = "",
      addressType = addType,
      created = System.currentTimeMillis(),
      updated = System.currentTimeMillis())
  }

  private def getHttpResult(commend: String, map: Map[String, String]): String = {
    val rq: Req = url(targetUrl).subject.POST <<? (Map("requestType" -> commend) ++ map)
    val result = Http(rq)
    result().getResponseBody
  }

  private def getIds(ids: String): Seq[String] =
    JSON.parseFull(ids).get.asInstanceOf[Array[String]].toSeq
}
