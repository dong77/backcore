package com.coinport.bitway.NxtBitway.processor

import scala.util.Random
import com.coinport.bitway.NxtBitway.mongo.NxtMongoDAO
import com.coinport.bitway.NxtBitway.http.NxtHttpClient
import com.coinport.coinex.data._
import com.coinport.coinex.data.Currency.Nxt
import com.coinport.bitway.NxtBitway.model._
import com.redis.RedisClient

/**
 * Created by chenxi on 7/18/14.
 */

class NxtProcessor(nxtMongo: NxtMongoDAO, nxtHttp: NxtHttpClient, redis: RedisClient) {
  val txsSet = Set.empty[String]
  var lastIndex = Currency.Nxt.toString + "last_index"
  val hotAccount = getOrGenerateHotAccount
  val prefix_transaction = "Nxt_tran_"
  val transfer_fee = 1.0

  def getRedisClient = redis

  def generateAddresses(gen: GenerateAddresses) = {
    val secretSeq = generateSecret(gen.num)
    val nxts = nxtHttp.getMultiAddresses(secretSeq, CryptoCurrencyAddressType.Unused)
    nxtMongo.insertAddresses(nxts)

    Some(BitwayMessage(
      currency = Nxt,
      generateAddressResponse = Some(GenerateAddressesResult(ErrorCode.Ok, Some(nxts.map(nxtAddress2Thrift).toSet)))
    ))
  }

  def syncHotAddresses(sync: SyncHotAddresses) = {
    val nxts = nxtMongo.queryByTypes(CryptoCurrencyAddressType.Hot)
    Some(BitwayMessage(
      currency = Nxt,
      syncHotAddressesResult = Some(SyncHotAddressesResult(ErrorCode.Ok, nxts.map(nxtAddress2Thrift).toSet))
    ))
  }

  def syncPrivateKeys(sync: SyncPrivateKeys) = {
    val nxts = nxtMongo.queryByAccountIds(sync.pubKeys.get.toSeq)
    Some(BitwayMessage(
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
    Some(getBitwayMessageWithReorgIndex(loop, nxtBlock, block))
  }

  def getNewBlock: Option[BitwayMessage] = {
    val blockStatus = nxtHttp.getBlockChainStatus()

    val height = redis.get[String](lastIndex).map(x => getRedisLastIndex(x)._2)
    height match {
      case None =>
        val nxtBlock = nxtHttp.getBlock(blockStatus.lastBlockId)
        redis.set(lastIndex, makeRedisLastIndex(nxtBlock.blockId, nxtBlock.height))
        Some(nxtBlock2Thrift(nxtBlock))

      case Some(blockHeight) =>
        val heightDiff = blockStatus.lastBlockHeight - blockHeight
        if (heightDiff == 0) None
        else if (heightDiff < 0) None // throws exception
        else {
          val nxtBlock =nxtHttp.getBlock(blockStatus.lastBlockId)
          redis.set(lastIndex, makeRedisLastIndex(nxtBlock.blockId, nxtBlock.height))
          Some(nxtBlock2Thrift(nxtBlock))
        }
    }
  }

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
      txs2 = txs2.filter(tx => senderIds.contains(tx.senderId) && recipientIds.contains(tx.recipientId))

      // model to thrift
      txs2.map { tx =>
          BitwayMessage(
            currency = Nxt,
            tx = Some(nxtTransaction2Thrift(tx))
          )
      }
    }
  }

  def sendMoney(transfer: TransferCryptoCurrency) = {
    val infos  = transfer.transferInfos
    transfer.`type` match {
      case TransferType.HotToCold | TransferType.Withdrawal =>
        infos.foreach{ info =>
          val txid = nxtHttp.sendMoney(hotAccount.secret, info.to.get, info.amount.get, transfer_fee)
          if(!txid.isEmpty) redis.set(getTransactionKey(txid), info.id)
        }

      case TransferType.UserToHot =>
        infos.foreach{ info =>
          val userSecret = nxtMongo.queryOneUser(info.from.get).get.secret
          val txid = nxtHttp.sendMoney(userSecret, hotAccount.accountId, info.amount.get, transfer_fee)
          if(!txid.isEmpty) redis.set(getTransactionKey(txid), info.id)
        }

      case x =>
    }
    None
  }

  def multiSendMoney(multiTransfer: MultiTransferCryptoCurrency) = {
    multiTransfer.transferInfos.map(transfers =>
      sendMoney(TransferCryptoCurrency(multiTransfer.currency, transfers._2, transfers._1))
    )
    None
  }

  private def generateSecret(addressNum: Int): Seq[String] = {
    val rand = new Random()
    val count = nxtMongo.countAddress()
    (0 until addressNum).map{ i =>
      "www.coinport.com" + "%%%" + rand.nextString(10) +
        (count + i) + "%%%" + rand.nextString(10) +
        System.currentTimeMillis() + "%%%" + rand.nextString(10)
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

  private def nxtAddress2Thrift(nxt: NxtAddress) = CryptoAddress(nxt.accountId, Some(nxt.secret), Some(nxt.accountRS))
  
  private def nxtTransaction2Thrift(tx: NxtTransaction): CryptoCurrencyTransaction = {
    CryptoCurrencyTransaction(
              sigId = Some(tx.fullHash),
              txid = Some(tx.transactionId),
              ids = Some(Seq(redis.get(getTransactionKey(tx.transactionId)).getOrElse("0").toLong)),
              inputs = Some(Seq(CryptoCurrencyTransactionPort(address = tx.recipientId, nxtRsAddress = Some(tx.recipientRS)))),
              outputs = Some(Seq(CryptoCurrencyTransactionPort(address = tx.senderId, nxtRsAddress = Some(tx.senderRS)))),
              status = TransferStatus.Accepted
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

  private def getTransactionKey(id: String) = prefix_transaction+id

  private def getBitwayMessageWithReorgIndex(loop: Boolean, blockNxt: NxtBlock, block: BlockIndex) = {
    if (loop == false) BitwayMessage(
      currency = Nxt,
      blockMsg = Some(CryptoCurrencyBlockMessage(
        reorgIndex = Some(BlockIndex(None, None)),
        block = CryptoCurrencyBlock(
          index = BlockIndex(None, None),
          prevIndex = BlockIndex(None, None),
          txs = Nil))))
    else {
      val nxtBlock = nxtHttp.getBlock(blockNxt.nextBlock.get)
      redis.set(lastIndex, nxtBlock.height.toString)
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
    (list(0), list(1).toInt)
  }

}