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
  val NXT2NQT = 10 * 10 * 10 * 10 * 10 * 10 * 10 * 10

  def getMultiAddresses(secretList: Seq[String], addType: CryptoCurrencyAddressType) =
    secretList.map(s => getAddress(s, addType))

  def getAddress(secret: String, addType: CryptoCurrencyAddressType): NxtAddress = {
    val queryMap = Map("secretPhrase" -> secret)
    val json = JSON.parseFull(getHttpResult("getAccountId", queryMap)).get.asInstanceOf[Map[String, String]]

    //todo(xichen): decode the secret
    NxtAddress(
      accountId = json.getOrElse("account", ""),
      accountRS = json.getOrElse("accountRS", ""),
      secret = secret,
      publicKey = json.get("publicKey"),
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

  def getBlockByHeight(height: Long): Option[NxtBlock] = {
    val queryMap = Map("height" -> height.toString)
    val json = JSON.parseFull(getHttpResult("getBlockId", queryMap)).get.asInstanceOf[Map[String, Any]]

    json.get("block") match {
      case Some(blockId) => Some(getBlock(blockId.asInstanceOf[String]))
      case None => None
    }
  }

  def getBlock(blockId: String) = {
    val queryMap = Map("block" -> blockId)
    val json = JSON.parseFull(getHttpResult("getBlock", queryMap)).get.asInstanceOf[Map[String, Any]]

    val txIds = json.get("transactions").map(x => x.asInstanceOf[Seq[String]]).getOrElse(Nil)
    val txs = getTransactions(txIds)

    NxtBlock(
      blockId = blockId,
      txs = txs,
      nextBlock = json.get("nextBlock").map(x => x.asInstanceOf[String]),
      previousBlock = json.get("previousBlock").get.asInstanceOf[String],
      timestamp = json.get("timestamp").get.asInstanceOf[Double].toLong,
      height = json.get("height").get.asInstanceOf[Double].toLong
    )
  }

  private def getTransactions(ids: Seq[String]) =
    ids.map{ tid =>
      val json = JSON.parseFull(getHttpResult("getTransaction", Map("transaction" -> tid))).get.asInstanceOf[Map[String, Any]]
      val x: Option[NxtTransaction] = json.get("type").get.asInstanceOf[Double].toInt match {
        case 0 =>
          Some(NxtTransaction(
            transactionId = json.get("transaction").get.asInstanceOf[String],
            senderId = json.get("sender").get.asInstanceOf[String],
            senderRS = json.get("senderRS").get.asInstanceOf[String],
            recipientId = json.get("recipient").get.asInstanceOf[String],
            recipientRS = json.get("recipientRS").get.asInstanceOf[String],
            timestamp = json.get("timestamp").get.asInstanceOf[Double].toLong,
            blockId = json.get("block").map(_.asInstanceOf[String]),
            height = json.get("height").get.asInstanceOf[Double].toInt,
            deadline = json.get("deadline").get.asInstanceOf[Double].toInt,
            tType = json.get("type").get.asInstanceOf[Double].toInt,
            confirms  = json.get("confirmations").map(_.asInstanceOf[Double].toInt),
            amountNQT = json.get("amountNQT").get.asInstanceOf[String].toLong,
            feeNQT = json.get("feeNQT").get.asInstanceOf[String].toLong,
            amount = json.get("amountNQT").get.asInstanceOf[String].toDouble/NXT2NQT,
            fee = json.get("feeNQT").get.asInstanceOf[String].toDouble/NXT2NQT,
            fullHash =  json.get("fullHash").get.asInstanceOf[String]
          ))
        case _ => None
      }
      x
    }.filter(x => x.isDefined).map(_.get)

  def getUnconfirmedTransactions() = {
    val queryMap = Map.empty[String, String]
    val json = JSON.parseFull(getHttpResult("getUnconfirmedTransactionIds", queryMap)).get.asInstanceOf[Map[String, String]]

    val txIds = json.getOrElse("unconfirmedTransactionIds", "[]").asInstanceOf[Seq[String]]
    getTransactions(txIds)
  }

  def sendMoney(secret: String, recipient: String, amount: Long, fee: Long, deadline: Int = 900) = {
    val queryMap = Map(
      "secretPhrase" -> secret,
      "recipient" -> recipient,
      "amountNQT" -> amount.toString,
      "feeNQT" -> fee.toString,
      "deadline" -> deadline.toString
    )

    val json = JSON.parseFull(getHttpResult("sendMoney", queryMap)).get.asInstanceOf[Map[String, String]]
    println("sendMoney>>response>>json"+json)

    NxtSendMoneyResponse(
      transactionId = json.get("transaction").getOrElse(""),
      fullHash = json.get("fullHash").getOrElse("")
    )
  }

  private def getHttpResult(commend: String, map: Map[String, String]): String = {
    val rq: Req = url(targetUrl).subject.POST <<? (Map("requestType" -> commend) ++ map)
    val result = Http(rq)
    result().getResponseBody
  }

  private def getIds(ids: String): Seq[String] =
    JSON.parseFull(ids).get.asInstanceOf[Array[String]].toSeq
}
