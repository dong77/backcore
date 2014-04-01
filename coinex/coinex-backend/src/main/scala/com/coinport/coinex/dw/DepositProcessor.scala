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

class DepositProcessor(db: MongoDB, accountProcessorPath: ActorPath) extends EventsourcedProcessor with ActorLogging with ChannelSupport {
  override val processorId = "coinex_dwp"

  val channelToAccountProcessor = createChannelTo("ap") // DO NOT CHANGE

  val deposits = new SimpleMongoCollection[Deposit, Deposit.Immutable] {
    val coll = db("deposits")
    def extractId(deposit: Deposit) = deposit.id
  }

  def receiveCommand = LoggingReceive {

    case m @ DoRequestCashDeposit(deposit) =>
      if (deposit.amount <= 0) {
        sender ! RequestCashDepositFailed(InvalidAmount)
      } else {
        val updated = deposit.copy(id = lastSequenceNr, created = Some(System.currentTimeMillis))
        persist(DoRequestCashDeposit(deposit = updated)) { event =>
          updateState(event)
          sender ! RequestCashDepositSucceeded(updated)
        }
      }

    case AdminConfirmCashDepositFailure(deposit, error) =>
      deposits.get(deposit.id) match {
        case Some(deposit) if deposit.status == TransferStatus.Pending =>
          persist(AdminConfirmCashDepositFailure(deposit, error))(updateState)

        case Some(_) => RequestCashDepositFailed(AlreadyConfirmed)

        case None => RequestCashDepositFailed(DepositNotExist)
      }

    case m: AdminConfirmCashDepositSuccess =>
      persist(m)(updateState)
  }

  def receiveRecover = {
    case event => updateState(event)
  }

  def updateState(event: Any) = event match {
    case DoRequestCashDeposit(deposit) =>
      deposits.put(deposit)

    case m @ AdminConfirmCashDepositSuccess(deposit) =>
      val updated = deposit.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Succeeded)
      deposits.put(updated)
      channelToAccountProcessor forward Deliver(Persistent(m), accountProcessorPath)

    case AdminConfirmCashDepositFailure(deposit, error) =>
      val updated = deposit.copy(updated = Some(System.currentTimeMillis), status = TransferStatus.Failed, reason = Some(error))
      deposits.put(updated)
  }
}
