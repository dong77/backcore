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
    secretList.map(s => getAddress(s, addType))

  def getAddress(secret: String, addType: CryptoCurrencyAddressType): NxtAddress = {
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


  def getBlockChainStatus() = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getBlockchainStatus", queryMap)).get.asInstanceOf[Map[String, Any]]

    NxtBlockStatus(
      timestamp = json.get("time").get.asInstanceOf[Double],
      lastBlockHeight = json.get("lastBlockchainFeederHeight").get.asInstanceOf[Double].toLong,
      lastBlockId =  json.get("lastBlock").get.asInstanceOf[String]
    )
  }

  def getBlock(blockId: String) = {
    val queryMap = Map("block" -> blockId)
    val json = JSON.parseFull(getHttpResult("getBlock", queryMap)).get.asInstanceOf[Map[String, Any]]
    json.foreach(println)


    val txIds = json.get("transactions").map(x => x.asInstanceOf[Seq[String]]).getOrElse(Nil)
    val txs = txIds.map { tid =>
      val tx = JSON.parseFull(getHttpResult("getTransaction", Map("transaction" -> tid))).get.asInstanceOf[Map[String, Any]]
      parseTransaction(tx)
    }.filter(_.tType == 0)

    NxtBlock(
      blockId = blockId,
      txs = txs,
      nextBlock = json.get("nextBlock").map(x => x.asInstanceOf[String]),
      previousBlock = json.get("previousBlock").get.asInstanceOf[String],
      timestamp = json.get("timestamp").get.asInstanceOf[Double].toLong,
      height = json.get("height").get.asInstanceOf[Double].toLong
    )
  }

  def parseTransaction(json: Map[String, Any]) = {
    NxtTransaction(
      transactionId = json.get("transaction").get.asInstanceOf[String],
      senderId = json.get("sender").get.asInstanceOf[String],
      senderRS = json.get("senderRS").get.asInstanceOf[String],
      recipientId = json.get("recipient").get.asInstanceOf[String],
      recipientRS = json.get("recipientRS").get.asInstanceOf[String],
      timestamp = json.get("timestamp").get.asInstanceOf[Double].toLong,
      blockId = json.get("block").get.asInstanceOf[String],
      height = json.get("height").get.asInstanceOf[Double].toInt,
      deadline = json.get("deadline").get.asInstanceOf[Double].toInt,
      tType = json.get("type").get.asInstanceOf[Double].toInt,
      confirms  = json.get("confirmations").get.asInstanceOf[Double].toInt,
      amount = json.get("amountNQT").get.asInstanceOf[String].toDouble,
      fee = json.get("feeNQT").get.asInstanceOf[String].toDouble,
      fullHash =  json.get("fullHash").get.asInstanceOf[String]
    )
  }

  def getUnconfirmedTransactions() = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getUnconfirmedTransactionIds", queryMap)).get.asInstanceOf[Map[String, String]]

    val trans = json.getOrElse("unconfirmedTransactionIds", "[]").asInstanceOf[Seq[Map[String, Any]]]
    trans.map(tran => parseTransaction(tran))
  }

  def sendMoney(secret: String, recipient: String, amount: Double, fee: Double, deadline: Int = 900): String = {
    val queryMap = Map(
      "secretPhrase" -> secret,
      "recipient" -> recipient,
      "amount" -> amount.toString,
      "fee" -> fee.toString,
      "deadline" -> deadline.toString
    )

    val json = JSON.parseFull(getHttpResult("sendMoney", queryMap)).get.asInstanceOf[Map[String, String]]
    json.get("transaction").getOrElse("")
  }

  private def getHttpResult(commend: String, map: Map[String, String]): String = {
    val rq: Req = url(targetUrl).subject.POST <<? (Map("requestType" -> commend) ++ map)
    val result = Http(rq)
    result().getResponseBody
  }

  private def getIds(ids: String): Seq[String] =
    JSON.parseFull(ids).get.asInstanceOf[Array[String]].toSeq
}
