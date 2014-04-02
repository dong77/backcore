package com.coinport.coinex.dw

import akka.persistence._
import akka.actor._
import com.coinport.coinex.common.ChannelSupport
import akka.event.LoggingReceive
import com.coinport.coinex.data._
import ErrorCode._
import com.coinport.coinex.serializers.ThriftEnumJson4sSerialization
import com.mongodb.util.JSON
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.{ MongoURI }
import com.coinport.coinex.common.SimpleMongoCollection

// TODO(d): Count fees
class DepositProcessor(db: MongoDB, accountProcessorPath: ActorPath)
  extends EventsourcedProcessor with ActorLogging with ChannelSupport {
  override val processorId = "coinex_dwp"

  val channelToAccountProcessor = createChannelTo("ap") // DO NOT CHANGE

  def receiveRecover = {
    case event => updateState(event)
  }

  def receiveCommand = LoggingReceive {
    case DoRequestCashDeposit(deposit) =>
      if (deposit.amount <= 0) {
        sender ! RequestCashDepositFailed(InvalidAmount)
      } else {
        val updated = deposit.copy(id = lastSequenceNr, created = Some(System.currentTimeMillis))
        persist(DoRequestCashDeposit(updated)) { event =>
          sender ! RequestCashDepositSucceeded(updated)
          updateState(event)
        }
      }

    case p @ ConfirmablePersistent(e: DoRequestCashWithdrawal) =>
      persist(e) { event =>
        p.confirm()
        updateState(event)
      }

    case AdminConfirmCashDepositFailure(deposit, error) =>
      deposits.get(deposit.id) match {
        case Some(deposit) if deposit.status == TransferStatus.Pending =>
          persist(AdminConfirmCashDepositFailure(deposit, error)) { event =>
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashDepositSuccess(deposit, _) =>
      deposits.get(deposit.id) match {
        case Some(deposit) if deposit.status == TransferStatus.Pending =>
          persist(AdminConfirmCashDepositSuccess(deposit, None)) { event =>
            updateState(event)
            deliverToAccountManager(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashWithdrawalFailure(withdrawal, error) =>
      withdrawals.get(withdrawal.id) match {
        case Some(withdrawal) if withdrawal.status == TransferStatus.Pending =>
          persist(AdminConfirmCashWithdrawalFailure(withdrawal, error)) { event =>
            deliverToAccountManager(event)
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }

    case AdminConfirmCashWithdrawalSuccess(withdrawal, _) =>
      withdrawals.get(withdrawal.id) match {
        case Some(withdrawal) if withdrawal.status == TransferStatus.Pending =>
          persist(AdminConfirmCashWithdrawalSuccess(withdrawal, None)) { event =>
            deliverToAccountManager(event)
            updateState(event)
            sender ! AdminCommandResult(Ok)
          }
        case Some(_) => sender ! AdminCommandResult(AlreadyConfirmed)
        case None => sender ! AdminCommandResult(DepositNotExist)
      }
  }

  def updateState(event: Any) = event match {
    case DoRequestCashDeposit(deposit) => deposits.put(deposit)
    case DoRequestCashWithdrawal(withdrawal) => withdrawals.put(withdrawal)

    case AdminConfirmCashDepositSuccess(deposit, _) =>
      val updated = deposit.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
      deposits.put(updated)

    case AdminConfirmCashDepositFailure(deposit, error) =>
      val updated = deposit.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
      deposits.put(updated)

    case AdminConfirmCashWithdrawalSuccess(withdrawal, _) =>
      val updated = withdrawal.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
      withdrawals.put(updated)

    case AdminConfirmCashWithdrawalFailure(withdrawal, error) =>
      val updated = withdrawal.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
      withdrawals.put(updated)

  }

  private def deliverToAccountManager(event: Any) =
    channelToAccountProcessor forward Deliver(Persistent(event), accountProcessorPath)

  private val deposits = new SimpleMongoCollection[Deposit, Deposit.Immutable] {
    val coll = db("deposits")
    def extractId(deposit: Deposit) = deposit.id
  }

  private val withdrawals = new SimpleMongoCollection[Withdrawal, Withdrawal.Immutable] {
    val coll = db("withdrawal")
    def extractId(withdrawal: Withdrawal) = withdrawal.id
  }
}