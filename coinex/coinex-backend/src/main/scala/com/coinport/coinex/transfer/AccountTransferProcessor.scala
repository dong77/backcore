package com.coinport.coinex.transfer

import akka.actor._
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import akka.persistence._
import com.mongodb.casbah.Imports._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.SimpleManager
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.common.support.ChannelSupport
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.data._
import ErrorCode._
import Implicits._
import TransferType._
import com.coinport.coinex.common.Manager

class AccountTransferProcessor(val db: MongoDB, accountProcessorPath: ActorPath) extends ExtendedProcessor
    with EventsourcedProcessor with TransferBehavior with ChannelSupport with ActorLogging {
  override def processorId = ACCOUNT_TRANSFER_PROCESSOR <<

  val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE
  val manager = new AccountTransferManager()

  def receiveRecover = updateState

  def receiveCommand = LoggingReceive {
    case p @ ConfirmablePersistent(DoRequestTransfer(w), _, _) =>
      persist(DoRequestTransfer(w.copy(id = manager.getTransferId))) {
        event =>
          p.confirm()
          sender ! RequestTransferSucceeded(event.transfer)
          updateState(event)
      }

    case AdminConfirmTransferFailure(transfer, error) =>
      transferHandler.get(transfer.id) match {
        case Some(transfer) if transfer.status == TransferStatus.Pending =>
          val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
          persist(AdminConfirmTransferFailure(updated, error)) { event =>
            sender ! AdminCommandResult(Ok)
            deliverToAccountManager(event)
            updateState(event)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmTransferSuccess(transfer) =>
      transferHandler.get(transfer.id) match {
        case Some(tranfer) if transfer.status == TransferStatus.Pending =>
          val updated = transfer.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
          transferHandler.put(updated)
          persist(AdminConfirmTransferSuccess(updated)) { event =>
            deliverToAccountManager(event)
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }
  }

  private def deliverToAccountManager(event: Any) =
    channelToAccountProcessor forward Deliver(Persistent(event), accountProcessorPath)
}

final class AccountTransferManager extends Manager[TAccountTransferState] {
  var lastTransferId = 1E12.toLong

  def getSnapshot = TAccountTransferState(lastTransferId, getFiltersSnapshot)

  override def loadSnapshot(s: TAccountTransferState) {
    lastTransferId = s.lastTransferId
    loadFiltersSnapshot(s.filters)
  }

  def getTransferId = lastTransferId + 1
  def setLastTransferId(id: Long) = { lastTransferId = id }
}

trait TransferBehavior {
  val db: MongoDB
  val manager: AccountTransferManager

  val transferHandler = new SimpleJsonMongoCollection[AccountTransfer, AccountTransfer.Immutable]() {
    lazy val coll = db("transfers")
    def extractId(item: AccountTransfer) = item.id
    def getQueryDBObject(q: QueryTransfer): MongoDBObject = {
      var query = MongoDBObject()
      if (q.uid.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.UserIdField.name -> q.uid.get)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.CurrencyField.name -> q.currency.get.name)
      if (q.`type`.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.TypeField.name -> q.`type`.get)
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + AccountTransfer.StatusField.name -> q.status.get.name)
      if (q.spanCur.isDefined) query ++= (DATA + "." + AccountTransfer.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }

  def updateState: Receive = {
    case DoRequestTransfer(t) =>
      transferHandler.put(t)
      manager.setLastTransferId(t.id)
    case AdminConfirmTransferSuccess(t) => transferHandler.put(t)
    case AdminConfirmTransferFailure(t, _) => transferHandler.put(t)
  }
}
