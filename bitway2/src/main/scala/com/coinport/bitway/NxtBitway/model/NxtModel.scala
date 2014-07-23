package com.coinport.bitway.NxtBitway.model

import com.coinport.coinex.data.CryptoCurrencyAddressType

case class NxtAddress(
                            accountId: String,
                            accountRS: String,
                            secret: String,
                            publicKey: String,
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
                           blockId: String,
                           amount: Double,
                           fee: Double,
                           timestamp: Long,
                           height: Int,
                           deadline: Int,
                           tType: Int,
                           confirms: Int)

case class NxtBlock(
                     blockId: String,
                     txs: Seq[NxtTransaction],
                     nextBlock: Option[String],
                     previousBlock: String,
                     timestamp: Long,
                     height: Long)

