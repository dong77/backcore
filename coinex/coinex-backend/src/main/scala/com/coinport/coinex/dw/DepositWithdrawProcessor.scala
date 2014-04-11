package com.coinport.coinex.dw

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._
import com.mongodb.casbah.Imports._

import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.SimpleManager
import com.coinport.coinex.common.mongo.SimpleJsonMongoCollection
import com.coinport.coinex.common.support.ChannelSupport
import com.coinport.coinex.data._
import ErrorCode._

// TODO(c): Count fees
class DepositWithdrawProcessor(val db: MongoDB, accountProcessorPath: ActorPath) extends ExtendedProcessor
    with EventsourcedProcessor with DepositWithdrawBehavior with ChannelSupport with ActorLogging {
  override def processorId = "coinex_dwp"

  val channelToAccountProcessor = createChannelTo("ap") // DO NOT CHANGE
  val manager = new SimpleManager()

  def receiveRecover = { case event => updateState(event) }

  def receiveCommand = LoggingReceive {
    case p @ ConfirmablePersistent(e: DoRequestCashWithdrawal, seq, _) => persist(e) { event => p.confirm(); updateState(event) }
    case p @ ConfirmablePersistent(e: DoRequestCashDeposit, seq, _) => persist(e) { event => p.confirm(); updateState(event) }

    case AdminConfirmCashDepositFailure(deposit, error) =>
      deposits.get(deposit.id) match {
        case Some(d) if d.status == TransferStatus.Pending =>
          val updated = d.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
          persist(AdminConfirmCashDepositFailure(updated, error)) { event =>
            sender ! AdminCommandResult(Ok)
            updateState(event)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashDepositSuccess(deposit) =>
      deposits.get(deposit.id) match {
        case Some(d) if d.status == TransferStatus.Pending =>
          val updated = d.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
          persist(AdminConfirmCashDepositSuccess(updated)) { event =>
            deliverToAccountManager(event)
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashWithdrawalFailure(withdrawal, error) =>
      withdrawals.get(withdrawal.id) match {
        case Some(w) if w.status == TransferStatus.Pending =>
          val updated = w.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
          persist(AdminConfirmCashWithdrawalFailure(updated, error)) { event =>
            deliverToAccountManager(event)
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashWithdrawalSuccess(withdrawal) =>
      withdrawals.get(withdrawal.id) match {
        case Some(w) if w.status == TransferStatus.Pending =>
          val updated = w.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
          persist(AdminConfirmCashWithdrawalSuccess(updated)) { event =>
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

trait DepositWithdrawBehavior {
  val db: MongoDB

  val deposits = new SimpleJsonMongoCollection[Deposit, Deposit.Immutable] {
    lazy val coll = db("deposits")
    def extractId(deposit: Deposit) = deposit.id
    def getQueryDBObject(q: QueryDeposit): MongoDBObject = {
      var query = MongoDBObject()
      if (q.uid.isDefined) query ++= MongoDBObject(DATA + "." + Deposit.UserIdField.name -> q.uid.get)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + Deposit.CurrencyField.name -> q.currency.get.getValue())
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + Deposit.StatusField.name -> q.status.get.getValue())
      if (q.spanCur.isDefined) query ++= (DATA + "." + Deposit.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }

  val withdrawals = new SimpleJsonMongoCollection[Withdrawal, Withdrawal.Immutable] {
    lazy val coll = db("withdrawal")
    def extractId(withdrawal: Withdrawal) = withdrawal.id
    def getQueryDBObject(q: QueryWithdrawal): MongoDBObject = {
      var query = MongoDBObject()
      if (q.uid.isDefined) query ++= MongoDBObject(DATA + "." + Withdrawal.UserIdField.name -> q.uid.get)
      if (q.currency.isDefined) query ++= MongoDBObject(DATA + "." + Withdrawal.CurrencyField.name -> q.currency.get.getValue())
      if (q.status.isDefined) query ++= MongoDBObject(DATA + "." + Withdrawal.StatusField.name -> q.status.get.getValue())
      if (q.spanCur.isDefined) query ++= (DATA + "." + Withdrawal.CreatedField.name $lte q.spanCur.get.from $gte q.spanCur.get.to)
      query
    }
  }

  def updateState(event: Any) = event match {
    case DoRequestCashDeposit(d) => deposits.put(d)
    case DoRequestCashWithdrawal(w) => withdrawals.put(w)
    case AdminConfirmCashDepositSuccess(d) => deposits.put(d)
    case AdminConfirmCashDepositFailure(d, _) => deposits.put(d)
    case AdminConfirmCashWithdrawalSuccess(w) => withdrawals.put(w)
    case AdminConfirmCashWithdrawalFailure(w, _) => withdrawals.put(w)
  }
}
