package com.coinport.coinex.transfer

import akka.actor.Actor._
import akka.event.LoggingAdapter
import com.coinport.coinex.data._
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.serializers.ThriftEnumJson4sSerialization
import com.mongodb.casbah.Imports._
import org.json4s._
import scala.collection.mutable.{ ListBuffer, Map }

import TransferType._

trait AccountTransferBehavior {

  val db: MongoDB
  implicit val manager: AccountTransferManager
  implicit val logger: LoggingAdapter
  var succeededRetainNum = collection.Map.empty[Currency, Int]
  val transferHandlerObjectMap = Map.empty[TransferType, CryptoCurrencyTransferBase]
  private val ONE_DAY = 24 * 3600 * 1000
  var needPersistCryptoMsg: Boolean = false

  def setSucceededRetainNum(succeededRetainNum: collection.Map[Currency, Int]) = {
    this.succeededRetainNum ++= succeededRetainNum
  }

  def isTransferByBitway(currency: Currency, transferConfig: Option[TransferConfig]): Boolean = {
    currency.value >= Currency.Btc.value && (transferConfig.isEmpty || !(transferConfig.get.manualCurrency.getOrElse(Set.empty[Currency]).contains(currency)))
  }

  def intTransferHandlerObjectMap() {
    val env = new TransferEnv(manager, transferHandler, transferItemHandler, logger, succeededRetainNum)
    transferHandlerObjectMap += Deposit -> CryptoCurrencyTransferDepositHandler.setEnv(env)
    transferHandlerObjectMap += UserToHot -> CryptoCurrencyTransferUserToHotHandler.setEnv(env)
    transferHandlerObjectMap += Withdrawal -> CryptoCurrencyTransferWithdrawalHandler.setEnv(env)
    transferHandlerObjectMap += HotToCold -> CryptoCurrencyTransferHotToColdHandler.setEnv(env)
    transferHandlerObjectMap += ColdToHot -> CryptoCurrencyTransferColdToHotHandler.setEnv(env)
    transferHandlerObjectMap += DepositHot -> CryptoCurrencyTransferDepositHotHandler.setEnv(env)
    transferHandlerObjectMap += DepositCnyByXrp -> CryptoCurrencyTransferDepositHotHandler.setEnv(env)
    transferHandlerObjectMap += UsersToInner -> CryptoCurrencyTransferUsersToInnerHandler.setEnv(env)
    manager.setTransferHandlers(transferHandlerObjectMap)
    CryptoCurrencyTransferUnknownHandler.setEnv(env)
  }

  def updateState: Receive = {

    case DoRequestTransfer(t, transferDebug, transferConfig) =>
      if (isTransferByBitway(t.currency, transferConfig) && !(transferDebug.isDefined && transferDebug.get)) {
        t.`type` match {
          case TransferType.Deposit => //Do nothing
          case TransferType.UserToHot =>
          case TransferType.DepositHot =>
          case TransferType.Withdrawal =>
          case TransferType.DepositCnyByXrp =>
            transferHandler.put(t)
          case TransferType.ColdToHot => //Just log, will confirmed by admin
            transferHandler.put(t)
          case TransferType.HotToCold =>
            transferHandler.put(t)
            val from = CryptoCurrencyTransactionPort("", None, Some(t.amount), Some(t.userId))
            val to = CryptoCurrencyTransactionPort("", None, Some(t.amount), Some(t.userId))
            prepareBitwayMsg(t, Some(from), Some(to), transferHandlerObjectMap(HotToCold), t.created)
          case TransferType.UsersToInner =>
          case TransferType.Unknown =>
            transferHandler.put(t)
        }
      } else {
        transferHandler.put(t)
      }
      if (t.`type` == TransferType.Withdrawal && transferConfig.get.enableAutoConfirm.getOrElse(false)) {
        manager.addUserId2Withdrawals(t)
      }
      manager.setLastTransferId(t.id)

    case AdminConfirmTransferFailure(t, _) =>
      if (t.status == TransferStatus.ConfirmBitwayFail || t.status == TransferStatus.HotInsufficientFail) { // should handle the bitway fail
        transferHandlerObjectMap(t.`type`).manualFailTransfer(t.id, t.status)
      }
      transferHandler.put(t)

    case DoCancelTransfer(t) => transferHandler.put(t)

    case AdminConfirmTransferSuccess(t, transferDebug, transferConfig) => {
      if (isTransferByBitway(t.currency, transferConfig) && !(transferDebug.isDefined && transferDebug.get)) {
        t.`type` match {
          case TransferType.Withdrawal if t.status == TransferStatus.Accepted =>
            val transferAmount = t.fee match {
              case Some(withdrawalFee: Fee) if withdrawalFee.amount > 0 => t.amount - withdrawalFee.amount
              case _ => t.amount
            }
            val to = CryptoCurrencyTransactionPort(t.address.get, None, Some(transferAmount), Some(t.userId), memo = t.memo, nxtPublicKey = t.nxtPublicKey)
            prepareBitwayMsg(t, None, Some(to), transferHandlerObjectMap(Withdrawal), t.updated)
          case TransferType.UserToHot if t.status == TransferStatus.Accepted =>
            val userToHotHandler = transferHandlerObjectMap(UserToHot)
            userToHotHandler.init()
            userToHotHandler.newHandlerFromAccountTransfer(t, None, None, t.updated)
          case _ => // Just handle other type, do nothing
        }
      }
      transferHandler.put(t)
    }

    case AdminConfirmTransferProcessed(t) => transferHandler.put(t)

    case m @ MultiCryptoCurrencyTransactionMessage(currency, txs, newIndex: Option[BlockIndex], confirmNum, timestamp, enableUsersToInner) =>
      needPersistCryptoMsg = false
      logger.info(s">>>>>>>>>>>>>>>>>>>>> updateState  => ${m.toString}")
      if (manager.getLastBlockHeight(currency) > 0) newIndex foreach {
        reOrgBlockIndex =>
          needPersistCryptoMsg = true
          transferHandlerObjectMap.values foreach {
            _.reOrganize(currency, reOrgBlockIndex, manager, timestamp)
          }
      }

      transferHandlerObjectMap.values foreach { _.init() }

      txs foreach {
        tx =>
          tx.txType match {
            case None =>
              logger.warning(s"Unexpected tx meet : ${currency.toString} ${tx.toString}")
              CryptoCurrencyTransferUnknownHandler.handleTx(currency, tx, None)
            case Some(txType) =>
              transferHandlerObjectMap.contains(txType) match {
                case true =>
                  needPersistCryptoMsg = true
                  if (txType == TransferType.DepositCnyByXrp) {
                    transferHandlerObjectMap(txType).handleTx(Currency.Xrprmb, tx, timestamp)
                  } else {
                    transferHandlerObjectMap(txType).handleTx(currency, tx, timestamp)
                  }
                case _ =>
                  logger.warning(s"Unknown tx meet : ${currency.toString} ${tx.toString}")
              }
          }
      }
      transferHandlerObjectMap.values foreach { _.checkConfirm(currency, timestamp, confirmNum, enableUsersToInner) }
      if (transferHandlerObjectMap.values.exists(_.msgBoxMap.nonEmpty))
        needPersistCryptoMsg = true
    // deprecated
    case rs @ TransferCryptoCurrencyResult(currency, _, request, timestamp) =>
      logger.info(s">>>>>>>>>>>>>>>>>>>>> updateState  => ${rs.toString}")
      transferHandlerObjectMap.values foreach { _.init() }
      request.get.transferInfos foreach {
        info =>
          transferHandlerObjectMap(request.get.`type`).handleBackcoreFail(info, currency, timestamp, info.error)
      }

    case mr @ MultiTransferCryptoCurrencyResult(currency, _, transferInfos, timestamp) =>
      logger.info(s">>>>>>>>>>>>>>>>>>>>> updateState  => ${mr.toString}")
      transferHandlerObjectMap.values foreach { _.init() }
      transferInfos.get.keys foreach {
        txType =>
          transferInfos.get.get(txType).get foreach {
            info =>
              transferHandlerObjectMap(txType).handleBackcoreFail(info, currency, timestamp, info.error)
          }
      }
  }

  def prepareBitwayMsg(transfer: AccountTransfer, from: Option[CryptoCurrencyTransactionPort],
    to: Option[CryptoCurrencyTransactionPort], handler: CryptoCurrencyTransferBase, timestamp: Option[Long]) {
    handler.init()
    handler.newHandlerFromAccountTransfer(transfer, from, to, timestamp)
  }

  def batchBitwayMessage(currency: Currency): Map[TransferType, List[CryptoCurrencyTransferInfo]] = {
    val multiCryptoCurrencyTransfers = Map.empty[TransferType, List[CryptoCurrencyTransferInfo]]
    transferHandlerObjectMap.keys map {
      key =>
        val infos = transferHandlerObjectMap(key).getMsgToBitway(currency)
        if (!infos.isEmpty)
          multiCryptoCurrencyTransfers.put(key, infos)
    }
    multiCryptoCurrencyTransfers
  }

  def batchAccountMessage(currency: Currency): Map[String, AccountTransfersWithMinerFee] = {
    val multiAccountTransfers = Map.empty[String, AccountTransfersWithMinerFee]
    transferHandlerObjectMap.keys map {
      key =>
        val sigId2AccountTransferMap: Map[String, (ListBuffer[AccountTransfer], Option[Long])] = transferHandlerObjectMap(key).getMsgToAccount(currency)
        if (!sigId2AccountTransferMap.isEmpty) {
          sigId2AccountTransferMap.keys map {
            sigId =>
              val tansfersWithMinerFee = sigId2AccountTransferMap(sigId)
              if (!tansfersWithMinerFee._1.isEmpty) {
                multiAccountTransfers.put(sigId, AccountTransfersWithMinerFee(tansfersWithMinerFee._1.toList, tansfersWithMinerFee._2))
              }
          }
        }
    }
    multiAccountTransfers

  }

  def canHotColdInterTransfer(currency: Currency, transferType: TransferType): Boolean = {
    !transferHandlerObjectMap(transferType).hasUnExpiredItems(System.currentTimeMillis() - ONE_DAY)
  }

  implicit val transferHandler = new SimpleJsonMongoCollection[AccountTransfer, AccountTransfer.Immutable]() {
    lazy val coll = db("transfers")
    override implicit val formats: Formats = ThriftEnumJson4sSerialization.formats + new FeeSerializer
    def extractId(item: AccountTransfer) = item.id

    def getQueryDBObject(q: QueryTransfer): MongoDBObject = {
      var query = MongoDBObject()
      if (q.uid.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.UserIdField.name -> q.uid.get)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.CurrencyField.name -> q.currency.get.name)
      if (q.types.nonEmpty) query ++= $or(q.types.map(t => DATA + "." + AccountTransfer.TypeField.name -> t.toString): _*)
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.StatusField.name -> q.status.get.name)
      if (q.spanCur.isDefined) query ++= (DATA + "." + AccountTransfer.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }

  implicit val transferItemHandler = new SimpleJsonMongoCollection[CryptoCurrencyTransferItem, CryptoCurrencyTransferItem.Immutable]() {
    lazy val coll = db("transferitems")
    def extractId(item: CryptoCurrencyTransferItem) = item.id

    def getQueryDBObject(q: QueryCryptoCurrencyTransfer): MongoDBObject = {
      var query = MongoDBObject()
      if (q.id.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.IdField.name -> q.id.get)
      if (q.sigId.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.SigIdField.name -> q.sigId.get)
      if (q.txid.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.TxidField.name -> q.txid.get.toString)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.CurrencyField.name -> q.currency.get.name)
      if (q.txType.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.TxTypeField.name -> q.txType.get.name)
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + CryptoCurrencyTransferItem.StatusField.name -> q.status.get.name)
      if (q.spanCur.isDefined) query ++= (DATA + "." + CryptoCurrencyTransferItem.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }
}

class FeeSerializer(implicit man: Manifest[Fee.Immutable]) extends CustomSerializer[Fee](format => ({
  case obj: JValue => Extraction.extract(obj)(ThriftEnumJson4sSerialization.formats, man)
}, {
  case x: Fee => Extraction.decompose(x)(ThriftEnumJson4sSerialization.formats)
}))

class TransferEnv(val manager: AccountTransferManager,
  val transferHandler: SimpleJsonMongoCollection[AccountTransfer, AccountTransfer.Immutable],
  val transferItemHandler: SimpleJsonMongoCollection[CryptoCurrencyTransferItem, CryptoCurrencyTransferItem.Immutable],
  val logger: LoggingAdapter,
  val succeededRetainNum: collection.immutable.Map[Currency, Int])

