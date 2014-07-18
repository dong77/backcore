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
                           timestamp: Long)

case class NxtBlock(
                     transactionIds: Seq[String],
                     nextBlock: String,
                     previousBlock: String,
                     timestamp: Long,
                     height: Long)

case class NxtTransaction(
                           transactionId: String,
                           senderId: String,
                           senderRS: String,
                           recipientId: String,
                           recipientRS: String,
                           blockId: String,
                           amount: String,
                           timestamp: Long,
                           height: Int,
                           deadline: Int,
                           subtype: Int,
                           confirms: Int)