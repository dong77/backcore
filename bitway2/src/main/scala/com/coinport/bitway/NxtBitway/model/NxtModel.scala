package com.coinport.bitway.NxtBitway.model

import com.coinport.coinex.data.CryptoCurrencyAddressType

case class NxtAddress(
                            accountId: String,
                            accountRS: String,
                            secret: String,
                            publicKey: Option[String],
                            addressType: CryptoCurrencyAddressType,
                            created: Long,
                            updated: Long)

case class NxtBlockStatus(
                           lastBlockHeight: Long,
                           lastBlockId: String,
                           timestamp: Double)

case class NxtTransaction(
                           transactionId: String,
                           fullHash: String,
                           senderId: String,
                           senderRS: String,
                           recipientId: String,
                           recipientRS: String,
                           blockId: Option[String],
                           amountNQT: Long,
                           feeNQT: Long,
                           timestamp: Long,
                           height: Int,
                           deadline: Int,
                           tType: Int,
                           confirms: Option[Int])

case class NxtBlock(
                     blockId: String,
                     txs: Seq[NxtTransaction],
                     nextBlock: Option[String],
                     previousBlock: String,
                     timestamp: Long,
                     height: Long)

case class NxtSendMoneyResponse(
                              transactionId: String,
                              fullHash: String
                              )

