package com.coinport.coinex.dw

import akka.persistence._
import akka.actor._
import com.coinport.coinex.common.ChannelSupport
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import ErrorCode._
import com.coinport.coinex.common.SimpleJsonMongoCollection
import com.mongodb.casbah.MongoDB

// TODO(c): Count fees
class DepositWithdrawProcessor(val db: MongoDB, accountProcessorPath: ActorPath)
    extends EventsourcedProcessor with DepositWithdrawBehavior with ChannelSupport with ActorLogging {
  override val processorId = "coinex_dwp"

  val channelToAccountProcessor = createChannelTo("ap") // DO NOT CHANGE

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

// TODO(xi): add more query method into `deposits` and `withdrawals`.
trait DepositWithdrawBehavior {
  val db: MongoDB
  val deposits = new SimpleJsonMongoCollection[Deposit, Deposit.Immutable] {
    val coll = db("deposits")
    def extractId(deposit: Deposit) = deposit.id
  }

  val withdrawals = new SimpleJsonMongoCollection[Withdrawal, Withdrawal.Immutable] {
    val coll = db("withdrawal")
    def extractId(withdrawal: Withdrawal) = withdrawal.id
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