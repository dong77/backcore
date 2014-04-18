package com.coinport.coinex.dw

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
import com.coinport.coinex.common.Manager

class DepositWithdrawProcessor(val db: MongoDB, accountProcessorPath: ActorPath) extends ExtendedProcessor
    with EventsourcedProcessor with DepositWithdrawBehavior with ChannelSupport with ActorLogging {
  override def processorId = DEPOSIT_WITHDRAW_PROCESSOR <<

  val channelToAccountProcessor = createChannelTo(ACCOUNT_PROCESSOR <<) // DO NOT CHANGE
  val manager = new DepositWithdrawManager()

  def receiveRecover = updateState

  def receiveCommand = LoggingReceive {
    case p @ ConfirmablePersistent(DoRequestCashWithdrawal(w), seq, _) =>
      persist(DoRequestCashWithdrawal(w.copy(id = manager.getDWId))) {
        event =>
          p.confirm()
          sender ! RequestCashWithdrawalSucceeded(event.withdrawal)
          updateState(event)
      }

    case p @ ConfirmablePersistent(DoRequestCashDeposit(d), seq, _) =>
      persist(DoRequestCashDeposit(d.copy(id = manager.getDWId))) {
        event =>
          p.confirm()
          sender ! RequestCashDepositSucceeded(event.deposit)
          updateState(event)
      }

    case AdminConfirmCashDepositFailure(deposit, error) =>
      dwHandler.get(deposit.id) match {
        case Some(dw: DWItem) if dw.status == TransferStatus.Pending =>
          val updated = dw.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
          persist(AdminConfirmCashDepositFailure(updated.toDeposit, error)) { event =>
            sender ! AdminCommandResult(Ok)
            updateState(event)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashDepositSuccess(deposit) =>
      dwHandler.get(deposit.id) match {
        case Some(dw) if dw.status == TransferStatus.Pending =>
          val updated = dw.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
          dwHandler.put(updated)
          persist(AdminConfirmCashDepositSuccess(updated.toDeposit)) { event =>
            deliverToAccountManager(event)
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashWithdrawalFailure(withdrawal, error) =>
      dwHandler.get(withdrawal.id) match {
        case Some(dw) if dw.status == TransferStatus.Pending =>
          val updated = dw.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
          persist(AdminConfirmCashWithdrawalFailure(updated.toWithdrawal, error)) { event =>
            deliverToAccountManager(event)
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashWithdrawalSuccess(withdrawal) =>
      dwHandler.get(withdrawal.id) match {
        case Some(dw) if dw.status == TransferStatus.Pending =>
          val updated = dw.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
          persist(AdminConfirmCashWithdrawalSuccess(updated.toWithdrawal)) { event =>
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

final class DepositWithdrawManager extends Manager[TDepositWithdrawState] {
  var lastDWId = 1E12.toLong

  def getSnapshot = TDepositWithdrawState(lastDWId, getFiltersSnapshot)

  override def loadSnapshot(s: TDepositWithdrawState) {
    lastDWId = s.lastDWId
    loadFiltersSnapshot(s.filters)
  }

  def getDWId = { lastDWId += 1; lastDWId }
}

trait DepositWithdrawBehavior {
  val db: MongoDB

  val dwHandler = new SimpleJsonMongoCollection[DWItem, DWItem.Immutable]() {
    lazy val coll = db("deposits_withdrawal")
    def extractId(item: DWItem) = item.id
    def getQueryDBObject(q: QueryDW): MongoDBObject = {
      var query = MongoDBObject()
      if (q.uid.isDefined) query ++= MongoDBObject(DATA + "." + DWItem.UserIdField.name -> q.uid.get)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + DWItem.CurrencyField.name -> q.currency.get.name)
      if (q.isDeposit.isDefined) query ++= MongoDBObject(DATA + "." + DWItem.IsDepositField.name -> q.isDeposit.get)
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + DWItem.StatusField.name -> q.status.get.name)
      if (q.spanCur.isDefined) query ++= (DATA + "." + DWItem.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }

  def updateState: Receive = {
    case m: DoRequestCashDeposit => dwHandler.put(m.deposit)
    case m: DoRequestCashWithdrawal => dwHandler.put(m.withdrawal)
    case m: AdminConfirmCashDepositSuccess => dwHandler.put(m.deposit)
    case m: AdminConfirmCashDepositFailure => dwHandler.put(m.deposit)
    case m: AdminConfirmCashWithdrawalSuccess => dwHandler.put(m.withdrawal)
    case m: AdminConfirmCashWithdrawalFailure => dwHandler.put(m.withdrawal)
  }
}
