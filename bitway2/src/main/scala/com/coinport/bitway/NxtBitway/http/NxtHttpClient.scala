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

    val jsonTXs = json.getOrElse("lastBlock", "[]").asInstanceOf[Seq[String]]
    val txs = jsonTXs.map(getTransaction)

    NxtBlock(
      blockId = blockId,
      txs = txs,
      nextBlock = json.getOrElse("lastBlock", "0"),
      previousBlock = json.getOrElse("previousBlock", "0"),
      timestamp = json.getOrElse("time", "0").toInt,
      height = json.getOrElse("height", "0").toLong
    )
  }

  def getTransaction(transaction: String) = {
    val json = JSON.parseFull(transaction).get.asInstanceOf[Map[String, String]]

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
      amount = json.getOrElse("amountNQT", "0"),
      fullHash =  json.getOrElse("fullHash", "")
    )
  }

  def getUnconfirmedTransactions() = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getUnconfirmedTransactionIds", queryMap)).get.asInstanceOf[Map[String, String]]

    val trans = json.getOrElse("unconfirmedTransactionIds", "[]").asInstanceOf[Seq[String]]
    trans.map(tran => getTransaction(tran))
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
