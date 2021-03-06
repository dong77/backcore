package com.coinport.bitway.NxtBitway.processor

import scala.util.Random
import com.coinport.bitway.NxtBitway.mongo.NxtMongoDAO
import com.coinport.bitway.NxtBitway.http.NxtHttpClient
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency.Nxt
import com.coinport.bitway.NxtBitway.model._
import java.util.UUID
import com.redis.RedisClient

/**
 * Created by chenxi on 7/18/14.
 */

class NxtProcessor(nxtMongo: NxtMongoDAO, nxtHttp: NxtHttpClient, redis: RedisClient) {
  val txsSet = scala.collection.mutable.Set.empty[String]
  var lastIndex = Currency.Nxt.toString + "last_index"
  val hotAccount = getOrGenerateHotAccount
  val prefix_transaction = "Nxt_tran_"
  val NXT2NQT = 10 * 10 * 10 * 10 * 10 * 10 * 10 * 10
  val transfer_fee = 1 * NXT2NQT

  def getRedisClient = redis

  def generateAddresses(gen: GenerateAddresses) = {
    val secretSeq = generateSecret(gen.num)
    var nxts = nxtHttp.getMultiAddresses(secretSeq, CryptoCurrencyAddressType.Unused)
    nxts = nxts.filter(!_.accountId.isEmpty).filter(!_.accountRS.isEmpty)

    nxts = nxtMongo.insertAddresses(nxts)

    Seq(BitwayMessage(
      currency = Nxt,
      generateAddressResponse = Some(GenerateAddressesResult(
        error = ErrorCode.Ok,
        addresses = Some(nxts.map(nxtAddress2Thrift).toSet),
        addressType = Some(CryptoCurrencyAddressType.Unused)))
    ))
  }

  def syncHotAddresses(sync: SyncHotAddresses) = {
    val nxts = nxtMongo.queryByTypes(CryptoCurrencyAddressType.Hot)
    Seq(BitwayMessage(
      currency = Nxt,
      syncHotAddressesResult = Some(SyncHotAddressesResult(ErrorCode.Ok, nxts.map(nxtAddress2Thrift).toSet))
    ))
  }

  def syncPrivateKeys(sync: SyncPrivateKeys) = {
    val nxts = nxtMongo.queryByAccountIds(sync.pubKeys.get.toSeq)
    Seq(BitwayMessage(
      currency = Nxt,
      syncPrivateKeysResult = Some(SyncPrivateKeysResult(ErrorCode.Ok, nxts.map(nxtAddress2Thrift).toSet))
    ))
  }

  def getMissedBlocks(gmccb: GetMissedCryptoCurrencyBlocks) = {
    val blockList = gmccb.startIndexs
    val (lastBlockId, lastBlockHeight)= getRedisLastIndex(redis.get[String](lastIndex).getOrElse(""))
    var block = blockList.last

    //get two same height block which from blockList & nxtHttp
    var (listIndex: Int, nxtBlock: NxtBlock) =
      if (lastBlockHeight > block.height.get) {
        var nBlock = nxtHttp.getBlock(lastBlockId)
        while (nBlock.height > block.height.get) nBlock = nxtHttp.getBlock(nBlock.previousBlock)
        (blockList.size - 1, nBlock)
      } else if (lastBlockHeight < block.height.get)
        (blockList.size - 1 - (block.height.get - lastBlockHeight), nxtHttp.getBlock(lastBlockId))

    //find the reorg Index
    var loop = true
    while (loop) {
      block = blockList(listIndex)
      if (block.id.get == nxtBlock.blockId) loop = false
      else {
        nxtBlock = nxtHttp.getBlock(nxtBlock.previousBlock)
        listIndex = listIndex - 1
      }
    }

    //get bitway message by reorg index
    Seq(getBitwayMessageWithReorgIndex(loop, nxtBlock, block))
  }

  def getNewBlock: Seq[BitwayMessage] = {
    val (lastBlockId: String, lastBlockHeight: Long)= redis.get[String](lastIndex) match {
      case Some(stringIndex) => getRedisLastIndex(stringIndex)
      case None =>
        val blockStatus = nxtHttp.getBlockChainStatus()
        (blockStatus.lastBlockId, blockStatus.lastBlockHeight - 1)

    }

    nxtHttp.getBlockByHeight(lastBlockHeight + 1L) match {
      case None=> Nil
      case Some(nxtBlock) =>
        redis.set(lastIndex, makeRedisLastIndex(nxtBlock.blockId, nxtBlock.height))
        Seq(nxtBlock2Thrift(nxtBlock))
    }
  }

  //by way of getting new block status to know the new block is shit
/*  def getNewBlock: Seq[BitwayMessage] = {
    val blockStatus = nxtHttp.getBlockChainStatus()
    val (lastBlockId, lastBlockHeight)= getRedisLastIndex(redis.get[String](lastIndex).getOrElse("-1//-1"))
    if (blockStatus.lastBlockHeight != lastBlockHeight) {
      println("last block id: %s, height: %d from nxt net:".format(blockStatus.lastBlockId, blockStatus.lastBlockHeight))
      println("last block id : %s, height: %d from redis:".format(lastBlockId, lastBlockHeight))

      if (lastBlockHeight < 0) {
        val nxtBlock = nxtHttp.getBlock(blockStatus.lastBlockId)
        redis.set(lastIndex, makeRedisLastIndex(nxtBlock.blockId, nxtBlock.height))
        Seq(nxtBlock2Thrift(nxtBlock))
      } else {
        var heightDiff = blockStatus.lastBlockHeight - lastBlockHeight
        var blockList = Seq.empty[NxtBlock]

        if (heightDiff == 0) Nil
        else if (heightDiff < 0) Nil
        else {

          var queryBlockId = blockStatus.lastBlockId
          var nxtBlock: NxtBlock = null
          while (heightDiff > 0) {
            nxtBlock = nxtHttp.getBlock(queryBlockId)
            blockList = blockList :+ nxtBlock
            queryBlockId = nxtBlock.previousBlock
            heightDiff = heightDiff - 1
          }
          redis.set(lastIndex, makeRedisLastIndex(blockList.head.blockId, blockList.head.height))
          blockList
        }
        blockList.reverse.map(nxtBlock2Thrift)
      }
    } else Nil
  }*/

  def getUnconfirmedTransactions: Seq[BitwayMessage] = {
    val txs = nxtHttp.getUnconfirmedTransactions()
    if (txs.isEmpty) {
      txsSet.empty
      Nil
    } else {
      // if the transaction is already in the set
      var txs2 = txs.filter(tx => !txsSet.contains(tx.fullHash))

      // if the transaction is about bitway user
      val senderIds = nxtMongo.queryByAccountIds(txs2.map(_.senderId)).map(_.accountId).toSet
      val recipientIds = nxtMongo.queryByAccountIds(txs2.map(_.recipientId)).map(_.accountId).toSet
      txs2 = txs2.filter(tx => senderIds.contains(tx.senderId) || recipientIds.contains(tx.recipientId))
      txs2.foreach(tx => txsSet.add(tx.fullHash))

      // model to thrift
      txs2.map { tx =>
          BitwayMessage(
            currency = Nxt,
            tx = Some(nxtTransaction2Thrift(tx))
          )
      }
    }
  }

  def sendMoney(transfer: TransferCryptoCurrency): Seq[BitwayMessage] = {
    val infos  = transfer.transferInfos
    val x: Seq[Option[CryptoCurrencyTransaction]] = transfer.`type` match {
      case TransferType.HotToCold | TransferType.Withdrawal =>
        infos.map{ info =>
          println("transfer type"+transfer.`type`)
          println("to"+info.to)
          println("amount"+info.amount)
          val txid = nxtHttp.sendMoney(hotAccount.secret, info.to.get, (info.amount.get * NXT2NQT).toLong, transfer_fee, info.nxtPublicKey.getOrElse(""))
          val y: Option[CryptoCurrencyTransaction] = if(!txid.fullHash.isEmpty){
            redis.set(getTransactionKey(txid.fullHash), info.id)
            None
          } else Some(CryptoCurrencyTransaction(status = TransferStatus.Rejected, ids = Some(Seq(info.id)), txType = Some(transfer.`type`)))
          y
        }
      case TransferType.UserToHot =>
        infos.map{ info =>
          println("transfer type"+transfer.`type`)
          println("to"+info.to)
          println("amount"+info.amount)
          val userSecret = nxtMongo.queryOneUser(info.from.get).get.secret
          val txid = nxtHttp.sendMoney(userSecret, hotAccount.accountId, (info.amount.get * NXT2NQT).toLong - transfer_fee, transfer_fee, info.nxtPublicKey.getOrElse(""))
          val y: Option[CryptoCurrencyTransaction] = if(!txid.fullHash.isEmpty) {
            redis.set(getTransactionKey(txid.fullHash), info.id)
            None
          } else Some(CryptoCurrencyTransaction(status = TransferStatus.Rejected, ids = Some(Seq(info.id)), txType = Some(transfer.`type`)))
          y
        }
      case _ => Nil
    }
    x.filter(_.isDefined).map{ cct =>
      BitwayMessage(
        currency = Nxt,
        tx = Some(cct.get)
      )
    }
  }

  def multiSendMoney(multiTransfer: MultiTransferCryptoCurrency): Seq[BitwayMessage] =
    multiTransfer.transferInfos.map(transfers =>
      sendMoney(TransferCryptoCurrency(multiTransfer.currency, transfers._2, transfers._1))
    ).flatten[BitwayMessage].toSeq

  private def generateSecret(addressNum: Int): Seq[String] = {
    val count = nxtMongo.countAddress()
    (0 until addressNum).map{ i =>
      "www.coinport.com" + "%%%" + UUID.randomUUID() +
        (count + i) + "%%%" + UUID.randomUUID() +
        System.currentTimeMillis() + "%%%" + UUID.randomUUID()
    }.toSeq
  }

  private def getOrGenerateHotAccount: NxtAddress = {
    val hotAddr = nxtMongo.queryOneByTypes(CryptoCurrencyAddressType.Hot)
    if (!hotAddr.isDefined) {
      val secret = generateSecret(1)(0)
      val addr = nxtHttp.getAddress(secret, CryptoCurrencyAddressType.Hot)
      nxtMongo.insertAddresses(Seq(addr))
      addr
    } else hotAddr.get
  }

  private def nxtAddress2Thrift(nxt: NxtAddress) = CryptoAddress(nxt.accountId, Some(nxt.secret), Some(nxt.accountRS), nxtPublicKey = Some(nxt.publicKey.get))
  
  private def nxtTransaction2Thrift(tx: NxtTransaction): CryptoCurrencyTransaction = {
    CryptoCurrencyTransaction(
              sigId = Some(tx.fullHash),
              txid = Some(tx.transactionId),
              ids = redis.get(getTransactionKey(tx.fullHash)).map(x => Seq(x.toLong)),
              outputs = Some(Seq(CryptoCurrencyTransactionPort(address = tx.recipientId, nxtRsAddress = Some(tx.recipientRS), amount = Some(tx.amount)))),
              inputs = Some(Seq(CryptoCurrencyTransactionPort(address = tx.senderId, nxtRsAddress = Some(tx.senderRS), amount = Some(tx.amount + tx.fee)))),
              status = TransferStatus.Confirming
            )
  }

  private def nxtBlock2Thrift(b: NxtBlock): BitwayMessage = {
    BitwayMessage(
      currency = Nxt,
      blockMsg = Some(CryptoCurrencyBlockMessage(
        reorgIndex = None,
        block = CryptoCurrencyBlock(
          index = BlockIndex(id = Some(b.blockId), height = Some(b.height)),
          prevIndex = BlockIndex(id = Some(b.previousBlock), height = Some(b.height - 1)),
          txs = b.txs.map(nxtTransaction2Thrift)
        ),
        timestamp = None)
      ))
  }

  private def getTransactionKey(fullHash: String) = prefix_transaction+fullHash

  private def getBitwayMessageWithReorgIndex(loop: Boolean, blockNxt: NxtBlock, block: BlockIndex) = {
    if (loop == true) BitwayMessage(
      currency = Nxt,
      blockMsg = Some(CryptoCurrencyBlockMessage(
        reorgIndex = Some(BlockIndex(None, None)),
        block = CryptoCurrencyBlock(
          index = BlockIndex(None, None),
          prevIndex = BlockIndex(None, None),
          txs = Nil))))
    else {
      val nxtBlock = nxtHttp.getBlock(blockNxt.nextBlock.get)
      redis.set(lastIndex, makeRedisLastIndex(nxtBlock.blockId, nxtBlock.height))
      BitwayMessage(
        currency = Nxt,
        blockMsg = Some(CryptoCurrencyBlockMessage(
          reorgIndex = Some(BlockIndex(block.id, block.height)),
          block = CryptoCurrencyBlock(
            index = BlockIndex(Some(nxtBlock.blockId), Some(nxtBlock.height)),
            prevIndex = BlockIndex(block.id, block.height),
            txs = nxtBlock.txs.map(nxtTransaction2Thrift)
          ))))}
  }

  private def makeRedisLastIndex(block: String, height: Long) = block + "//" + height

  private def getRedisLastIndex(value: String) = {
    val list = value.split("//")
    (list(0), list(1).toLong)
  }
}
