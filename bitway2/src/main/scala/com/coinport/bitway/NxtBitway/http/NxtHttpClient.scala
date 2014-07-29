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
      accountId = json.getOrElse("accountId", ""),
      accountRS = json.getOrElse("accountRS", ""),
      secret = secret,
      publicKey = None,
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

      NxtTransaction(
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
      )
    }.filter(_.tType == 0)

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


  val x =
    """
      |{
      |    "unconfirmedTransactions": [
      |        {
      |            "fullHash": "2c4da2df7ef99ab761613775dc8a9ef3ea8603a039226c4f66a50861dc31f6d5",
      |            "signatureHash": "639cca81fe007dfb994f2ec7471795d6891f9311338ceb6654ac56ca4e111f81",
      |            "transaction": "13230161178667404588",
      |            "amountNQT": "0",
      |            "attachment": {
      |                "alias": "BitMafia",
      |                "uri": ""
      |            },
      |            "recipientRS": "NXT-MRCC-2YLS-8M54-3CMAJ",
      |            "type": 1,
      |            "feeNQT": "100000000",
      |            "recipient": "1739068987193023818",
      |            "sender": "5083605532342630200",
      |            "timestamp": 20975165,
      |            "height": 2147483647,
      |            "subtype": 1,
      |            "senderPublicKey": "f71861284bfe43860e6a96c573e8314f20bdb3176717d275939887e5ee7b7b32",
      |            "deadline": 1440,
      |            "senderRS": "NXT-WFTS-BJQR-3V25-6S65F",
      |            "signature": "125cbc809ca015a62715ef5943a1b15f017a66e014a4148a15ebc7689293100e8c6a3169346b2f3e5b3d24c551b17e0218a59417018a94aff850a6852e6e6dce"
      |        },
      |        {
      |            "fullHash": "082ed743de5d895b23191d16edf848d0e127f9fae79aaded2804e3d7ce5fe997",
      |            "signatureHash": "a32cbfa42e093a1d53ec9c2b867ceac5adca6a905de9746f5a8b233fec3301e6",
      |            "transaction": "6595906338463100424",
      |            "amountNQT": "300000000",
      |            "recipientRS": "NXT-FDKT-PDM6-U5TW-GLJ4H",
      |            "type": 0,
      |            "feeNQT": "100000000",
      |            "recipient": "16684168294764555833",
      |            "sender": "12460950813220838871",
      |            "timestamp": 20975264,
      |            "height": 2147483647,
      |            "subtype": 0,
      |            "senderPublicKey": "d2713456e6693fd5c536d1085c2df779c73486792ad8b6db272df2c27150fd2a",
      |            "deadline": 1440,
      |            "senderRS": "NXT-B9GR-RTJU-H8QC-CJKVT",
      |            "signature": "3bb7a95ef2b766c1566be35e8196b81e1f2d908841ce71a55fbe098719da65016d535fb27956df24544cfe53ccc52ac067323f08e559a8f87c7b8f9f1aa92a5a"
      |        },
      |        {
      |            "fullHash": "82be312ab10ee84d8a58fcb8b63c861f45d4c3dd6011ad3f4da63b30b5784a1d",
      |            "signatureHash": "fdf8df8ed150062ae228114aab0a7d23f397dced802e4b5a3c5e4ce01ca91fd3",
      |            "transaction": "5613753089597226626",
      |            "amountNQT": "0",
      |            "attachment": {
      |                "alias": "MAF1A",
      |                "uri": ""
      |            },
      |            "recipientRS": "NXT-MRCC-2YLS-8M54-3CMAJ",
      |            "type": 1,
      |            "feeNQT": "100000000",
      |            "recipient": "1739068987193023818",
      |            "sender": "5083605532342630200",
      |            "timestamp": 20975131,
      |            "height": 2147483647,
      |            "subtype": 1,
      |            "senderPublicKey": "f71861284bfe43860e6a96c573e8314f20bdb3176717d275939887e5ee7b7b32",
      |            "deadline": 1440,
      |            "senderRS": "NXT-WFTS-BJQR-3V25-6S65F",
      |            "signature": "e2eefbed290c4d76096f42c17c8d2d66933f8f8a2a4396f044509d2e9c518a0263087ca9edb504aa1895e892c0d38f186b574c294a1d5c5a1766b744f609d01e"
      |        },
      |        {
      |            "fullHash": "dee6f7801c824004d65b88eefe3bbf1c5361c2550713b9e846507fd1cfd68c2d",
      |            "signatureHash": "8a15eee362bf746de386435f2320a9631c05ca901d0eb57838a7f7ac3ec6df98",
      |            "transaction": "306387833595619038",
      |            "amountNQT": "200000000",
      |            "recipientRS": "NXT-FDKT-PDM6-U5TW-GLJ4H",
      |            "type": 0,
      |            "feeNQT": "100000000",
      |            "recipient": "16684168294764555833",
      |            "sender": "16875711819248071735",
      |            "timestamp": 20975308,
      |            "height": 2147483647,
      |            "subtype": 0,
      |            "senderPublicKey": "0ef66a68add54efe372e5a24e101b059ef0353561065daf8647ea0445ff68a15",
      |            "deadline": 1440,
      |            "senderRS": "NXT-AX3R-HMZ4-NPLJ-GLNEN",
      |            "signature": "6628fd49b58d4bdfbacd9221b33a8a682d5d32dbe8dd202968259c06f1e4d404dc5da2f15d6824dc1c805327ffb77601cd87521cf11ce20ae29e584ce2bc0a44"
      |        },
      |        {
      |            "fullHash": "6f057d8243639bb34eb9de9efa8b29735cab86eb6498f7c786614cd51e68f68b",
      |            "signatureHash": "c43e54d646df60976e4c661da0e2794d6dde1830c8422c69a6d28f5ddd9f2a25",
      |            "transaction": "12942047095782442351",
      |            "amountNQT": "300000000",
      |            "recipientRS": "NXT-FDKT-PDM6-U5TW-GLJ4H",
      |            "type": 0,
      |            "feeNQT": "100000000",
      |            "recipient": "16684168294764555833",
      |            "sender": "6422470802999140449",
      |            "timestamp": 20975354,
      |            "height": 2147483647,
      |            "subtype": 0,
      |            "senderPublicKey": "090a686b29366faa2ac16ea36c1c21f294106719f14f5f458d726212b69a0e3f",
      |            "deadline": 1440,
      |            "senderRS": "NXT-BS53-7DNK-ASC4-7MBAL",
      |            "signature": "65c7a13c124765c8671279c6dbcf1376676ccff40c8566794a0b6b0a8a55a00b1579f87fc325ebaddd23acafcc20dc9a9dbfeaab83f10b56a2021a210399e0cf"
      |        },
      |        {
      |            "fullHash": "6ed9dbd9388356d4e5ad9123d1635491e2b403827a2d53880ac9db046706c2d1",
      |            "signatureHash": "22dad0f203a8f50def94c7078ac1dca8d9fd52e7fee7d6a410eb45ab74c76a2d",
      |            "transaction": "15300561064234309998",
      |            "amountNQT": "100000000",
      |            "recipientRS": "NXT-FDKT-PDM6-U5TW-GLJ4H",
      |            "type": 0,
      |            "feeNQT": "100000000",
      |            "recipient": "16684168294764555833",
      |            "sender": "18411188220294446357",
      |            "timestamp": 20975287,
      |            "height": 2147483647,
      |            "subtype": 0,
      |            "senderPublicKey": "c4135f8599e539fb4d745fa693d2024bce07335d441b0b4815c10bbfb93f3719",
      |            "deadline": 1440,
      |            "senderRS": "NXT-5HAP-6H3S-BZ7S-HGF2Z",
      |            "signature": "524ac9810cb644275e7c17c4788babf981cab4a26655ec5e54d5e488db32ab0d795b5a60878bab174de1311f4ab163647e48b44cfcd9bcbeaae5f4bd190cb1d7"
      |        },
      |        {
      |            "fullHash": "7ccdacd426e35fe94eaf8fddfba6a2d34586312c17cffb1d7d7837c011a85b89",
      |            "signatureHash": "ea24009daa14fe97479c0aad8e7bbe58ef86ac9bd272415d30fdcb3ceb5be31e",
      |            "transaction": "16816409289541078396",
      |            "amountNQT": "300000000",
      |            "recipientRS": "NXT-FDKT-PDM6-U5TW-GLJ4H",
      |            "type": 0,
      |            "feeNQT": "100000000",
      |            "recipient": "16684168294764555833",
      |            "sender": "18147390648216896721",
      |            "timestamp": 20975246,
      |            "height": 2147483647,
      |            "subtype": 0,
      |            "senderPublicKey": "c91ae084d51ebf4c6bb6a2cb39da1470c9bd39fbdcab955c5ce05923c328631d",
      |            "deadline": 1440,
      |            "senderRS": "NXT-FK8K-NP3M-KAXX-HV5QR",
      |            "signature": "5c7c5158be9d6ea0917bed53316c5ab66390d2cbc4dbe570304f425f5c12400eaf9f94e86886f91b6d7dfed818bc69b04d77c15b30ec916cfd3b94eb4aca2f4a"
      |        },
      |        {
      |            "fullHash": "ba527eb5f33ab34d0f67ff1b72f1c34775596ddea6226f62cdace7ffe3bdf43f",
      |            "signatureHash": "0dc8dfb24cda63d52509ff1f6b68f0aae101fd4f5f779b071f5cf182b0625813",
      |            "transaction": "5598883580148077242",
      |            "amountNQT": "300000000",
      |            "recipientRS": "NXT-FDKT-PDM6-U5TW-GLJ4H",
      |            "type": 0,
      |            "feeNQT": "100000000",
      |            "recipient": "16684168294764555833",
      |            "sender": "2767859287517333933",
      |            "timestamp": 20975336,
      |            "height": 2147483647,
      |            "subtype": 0,
      |            "senderPublicKey": "7756634a2e778c7a5468b9b043b38f0aa753ee045d012105cc4639ea1a15173d",
      |            "deadline": 1440,
      |            "senderRS": "NXT-T4FF-DFUD-E3TB-4BDUE",
      |            "signature": "044b41813874ec9e529be66c4afe84a0b82b269c0c41d4ea116df7837d64aa0406a265cd03bfda21f9332beccc56cf70a07a5d338b2d5115b01058b3995f2285"
      |        },
      |        {
      |            "fullHash": "8c93fcf22295728ade6d614066eef0c5e8da932920afe21f329a249857cb356f",
      |            "signatureHash": "4ed10f2f5b256fb59fce3c71bf6f71aa0cfa4a2a120b7016cdaebe4c6e644df0",
      |            "transaction": "9976200101917135756",
      |            "amountNQT": "200000000",
      |            "recipientRS": "NXT-FDKT-PDM6-U5TW-GLJ4H",
      |            "type": 0,
      |            "feeNQT": "100000000",
      |            "recipient": "16684168294764555833",
      |            "sender": "4645628797628466264",
      |            "timestamp": 20975373,
      |            "height": 2147483647,
      |            "subtype": 0,
      |            "senderPublicKey": "539d548944921131d8b739682a6795523d024472022b6998a5059d4e08c2fa76",
      |            "deadline": 1440,
      |            "senderRS": "NXT-JK4S-SWJH-89DM-6Q6Y2",
      |            "signature": "4ba2e93cb6114e0146f9da1845d9ac683c15e1c05165f59e4d3f2ac97172cb0762cfc241d95bf5635869b5c3cd9ded15e804c9aed0fd1db87bcd76e7c8272c41"
      |        }
      |    ]
      |}
    """.stripMargin
}
