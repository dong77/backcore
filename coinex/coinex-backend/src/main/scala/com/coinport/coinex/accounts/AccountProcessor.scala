/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.accounts

import akka.actor._
import akka.actor.Actor.Receive
import akka.event.{ LoggingAdapter, LoggingReceive }
import akka.persistence.SnapshotOffer
import akka.persistence._

import com.coinport.coinex.common._
import com.coinport.coinex.common.Constants._
import com.coinport.coinex.common.PersistentId._
import com.coinport.coinex.common.ExtendedProcessor
import com.coinport.coinex.common.support._
import com.coinport.coinex.data._
import com.coinport.coinex.fee._
import ErrorCode._
import Implicits._
import TransferType._
import Currency._

class AccountProcessor(
  marketProcessors: Map[MarketSide, ActorRef],
  marketUpdateProcessoressorPath: ActorPath,
  depositWithdrawProcessorPath: ActorPath,
  accountConfig: AccountConfig) extends ExtendedProcessor with EventsourcedProcessor
    with AccountManagerBehavior with ActorLogging {

  val feeConfig = accountConfig.feeConfig
  override implicit val logger = log

  private val MAX_PRICE = 1E8.toDouble // 100000000.00000001 can be preserved by toDouble.

  override val processorId = ACCOUNT_PROCESSOR <<
  val channelToMarketProcessors = createChannelTo(MARKET_PROCESSOR <<) // DO NOT CHANGE
  val channelToMarketUpdateProcessor = createChannelTo(MARKET_UPDATE_PROCESSOR<<) // DO NOT CHANGE
  val channelToDepositWithdrawalProcessor = createChannelTo(ACCOUNT_TRANSFER_PROCESSOR<<) // DO NOT CHANGE
  val manager = new AccountManager(1E12.toLong)

  override def identifyChannel: PartialFunction[Any, String] = {
    case as: AdminConfirmTransferSuccess => "tsf"
    case af: AdminConfirmTransferFailure => "tsf"
    case rt: RequestTransferFailed => "tsf"
    case cs: CryptoTransferSucceeded => "tsf"
    case cf: CryptoTransferFailed => "tsf"
    case cr: CryptoTransferResult => "tsf"
    case cl: DoCancelTransfer => "tsf"
    case OrderSubmitted(originOrderInfo, txs) => "mp_" + originOrderInfo.side.s
    case OrderCancelled(side, order) => "mp_" + side.s
  }

  def receiveRecover = PartialFunction.empty[Any, Unit]

  def receiveCommand = LoggingReceive {
    case DoRequestTransfer(t, _, _) => t.`type` match {
      case Withdrawal =>
        val adjustment = CashAccount(t.currency, -t.amount, 0, t.amount)
        if (!manager.canUpdateCashAccount(t.userId, adjustment)) {
          sender ! RequestTransferFailed(InsufficientFund)
        } else {
          val updated = countFee(t.copy(created = Some(System.currentTimeMillis)))
          if (!isFeeSubToAmount(updated)) {
            sender ! RequestTransferFailed(InvalidAmount)
          } else {
            persist(DoRequestTransfer(updated)) { event =>
              updateState(event)
              channelToDepositWithdrawalProcessor forward Deliver(Persistent(event), depositWithdrawProcessorPath)
            }
          }
        }

      case Deposit =>
        if (t.amount <= 0) {
          sender ! RequestTransferFailed(InvalidAmount)
        } else {
          val updated = countFee(t.copy(created = Some(System.currentTimeMillis)))
          persist(DoRequestTransfer(updated)) { event =>
            updateState(event)
            channelToDepositWithdrawalProcessor forward Deliver(Persistent(event), depositWithdrawProcessorPath)
          }
        }

      case _ => // frontend can't send UserToHot, HotToCold, ColdToHot
    }

    case DoRequestPayment(payment) =>
      val adjustment = CashAccount(payment.currency, -payment.amount, 0, 0)
      if ((payment.payer != NULL_USER_ID && payment.payer < FIRST_USER_ID) ||
        (payment.payee != NULL_USER_ID && payment.payee < FIRST_USER_ID)) {
        sender ! RequestPaymentResult(payment.currency, ErrorCode.InvalidUser)
      } else if (payment.amount <= 0) {
        sender ! RequestPaymentResult(payment.currency, ErrorCode.InvalidAmount)
      } else if (payment.payer != NULL_USER_ID && !manager.canUpdateCashAccount(payment.payer, adjustment)) {
        sender ! RequestPaymentResult(payment.currency, ErrorCode.InsufficientFund)
      } else {
        val updated = payment.copy(id = manager.getLastPaymentId(), created = Some(System.currentTimeMillis))
        persist(DoRequestPayment(updated)) { event =>
          updateState(event)
          sender ! RequestPaymentResult(payment.currency, ErrorCode.Ok)
        }
      }

    case p @ ConfirmablePersistent(m: AdminConfirmTransferSuccess, _, _) =>
      persist(m.copy(transfer = appendFeeIfNecessary(m.transfer))) { event =>
        confirm(p)
        updateState(event)
      }

    case p @ ConfirmablePersistent(m: AdminConfirmTransferProcessed, _, _) =>
      persist(m.copy(transfer = appendFeeIfNecessary(m.transfer))) {
        event =>
          confirm(p)
          updateState(event)
      }

    case p @ ConfirmablePersistent(m: CryptoTransferSucceeded, _, _) =>
      persist(m.copy(transfers = m.transfers.map(appendFeeIfNecessary(_)))) {
        event =>
          confirm(p)
          updateState(event)
      }

    case m: CryptoTransferResult =>
      handleCryptoTransferResult(m)

    case p @ ConfirmablePersistent(m: CryptoTransferResult, _, _) =>
      confirm(p)
      handleCryptoTransferResult(m)

    case DoRequestGenerateABCode(userId, amount, _, _) => {
      val adjustment = CashAccount(Currency.Cny, -amount, amount, 0)
      if (!manager.canUpdateCashAccount(userId, adjustment)) {
        sender ! RequestGenerateABCodeFailed(InsufficientFund)
      } else {
        val (a, b) = manager.generateABCode()
        persist(DoRequestGenerateABCode(userId, amount, Some(a), Some(b))) { event =>
          updateState(event)
          sender ! RequestGenerateABCodeSucceeded(a, b)
        }
      }
    }

    case DoRequestACodeQuery(userId, codeA) => {
      if (!manager.isCodeAAvailable(userId, codeA)) {
        sender ! RequestACodeQueryFailed(LockedACode)
      } else {
        persist(DoRequestACodeQuery(userId, codeA)) { event =>
          updateState(event)
          sender ! RequestACodeQuerySucceeded(codeA, RechargeCodeStatus.Frozen,
            manager.abCodeMap(manager.codeAIndexMap(codeA)).amount)
        }
      }
    }

    case DoRequestBCodeRecharge(userId, codeB) => {
      val (canRecharge, error) = manager.isCodeBAvailable(userId, codeB)
      canRecharge match {
        case false => sender ! RequestBCodeRechargeFailed(error.asInstanceOf[ErrorCode])
        case true => {
          persist(DoRequestBCodeRecharge(userId, codeB)) { event =>
            updateState(event)
            sender ! RequestBCodeRechargeSucceeded(codeB, RechargeCodeStatus.Confirming,
              manager.abCodeMap(manager.codeBIndexMap(codeB)).amount)
          }
        }
      }
    }

    case DoRequestConfirmRC(userId, codeB, amount) => {
      val (canRecharge, error) = manager.verifyConfirm(userId, codeB)
      canRecharge match {
        case false => sender ! RequestConfirmRCFailed(error.asInstanceOf[ErrorCode])
        case true => {
          persist(DoRequestConfirmRC(userId, codeB, amount)) { event =>
            updateState(event)
            sender ! RequestConfirmRCSucceeded(codeB, RechargeCodeStatus.RechargeDone,
              manager.abCodeMap(manager.codeBIndexMap(codeB)).amount)
          }
        }
      }
    }

    case p @ ConfirmablePersistent(m: AdminConfirmTransferFailure, _, _) =>
      persist(m) { event => confirm(p); updateState(event) }

    case p @ ConfirmablePersistent(m: CryptoTransferFailed, _, _) =>
      persist(m) { event => confirm(p); updateState(event) }

    case p @ ConfirmablePersistent(m: RequestTransferFailed, _, _) =>
      log.error(s"Failed by AccountTransferProcessor for reason: ${m.error.toString}")

    case p @ ConfirmablePersistent(m: DoCancelTransfer, _, _) =>
      persist(m) { event => confirm(p); updateState(event) }

    case DoSubmitOrder(side, order) =>
      if (order.quantity <= 0) {
        sender ! SubmitOrderFailed(side, order, ErrorCode.InvalidAmount)
      } else {
        val adjustment = CashAccount(side.outCurrency, -order.quantity, order.quantity, 0)
        if (!manager.canUpdateCashAccount(order.userId, adjustment)) {
          sender ! SubmitOrderFailed(side, order, ErrorCode.InsufficientFund)
        } else {
          val updated = order.copy(
            id = manager.getOrderId,
            timestamp = Some(System.currentTimeMillis))

          if (updated.price.isDefined && (updated.price.get == 0.0 || updated.price.get > MAX_PRICE)) {
            sender ! SubmitOrderFailed(side, order, ErrorCode.PriceOutOfRange)
          } else {
            persist(DoSubmitOrder(side, updated)) { event =>
              channelToMarketProcessors forward Deliver(Persistent(OrderFundFrozen(side, updated)), getProcessorPath(side))
              updateState(event)
            }
          }
        }
      }

    case p @ ConfirmablePersistent(event: OrderSubmitted, seq, _) =>
      confirm(p)
      persist(countFee(event)) { event =>
        sender ! event
        updateState(event)
        channelToMarketUpdateProcessor forward Deliver(Persistent(event), marketUpdateProcessoressorPath)
      }

    case p @ ConfirmablePersistent(event: OrderCancelled, seq, _) =>
      confirm(p)
      persist(countFee(event)) { event =>
        sender ! event
        updateState(event)
        channelToMarketUpdateProcessor forward Deliver(Persistent(event), marketUpdateProcessoressorPath)
      }

    // direct message OrderCancelled is only for manual fixing bug, not programing usage
    case m: OrderCancelled =>
      persist(m) { event =>
        updateState(event)
      }
  }

  private def getProcessorPath(side: MarketSide): ActorPath = {
    marketProcessors.getOrElse(side, marketProcessors(side.reverse)).path
  }

  private def appendFeeIfNecessary(t: AccountTransfer) = {
    if ((t.`type` == Withdrawal || t.`type` == Deposit || t.`type` == DepositHot) && !t.fee.isDefined && t.status == TransferStatus.Succeeded) {
      countFee(t)
    } else {
      t
    }
  }

  private def handleCryptoTransferResult(m: CryptoTransferResult) {
    persist(m.copy(multiTransfers = m.multiTransfers.map(kv => kv._1 -> kv._2.copy(transfers = kv._2.transfers map { appendFeeIfNecessary(_) })))) {
      event =>
        updateState(event)
    }
  }

  private def isFeeSubToAmount(transfer: AccountTransfer): Boolean = {
    transfer.fee match {
      case Some(f) if transfer.amount > 0 && transfer.amount - f.amount > 0 => true
      case None if transfer.amount > 0 => true
      case _ => false
    }
  }
}

trait AccountManagerBehavior extends CountFeeSupport {
  val manager: AccountManager
  implicit val logger: LoggingAdapter

  def updateState: Receive = {

    case DoRequestGenerateABCode(userId, amount, Some(a), Some(b)) =>
      manager.createABCodeTransaction(userId, a, b, amount)
      manager.updateCashAccount(userId, CashAccount(Currency.Cny, -amount, amount, 0))

    case DoRequestACodeQuery(userId, codeA) => manager.freezeABCode(userId, codeA)

    case DoRequestBCodeRecharge(userId, codeB) => manager.bCodeRecharge(userId, codeB)

    case DoRequestConfirmRC(userId, codeB, amount) => {
      manager.confirmRecharge(userId, codeB)
      manager.updateCashAccount(userId, CashAccount(Currency.Cny, 0, -amount, 0))
      manager.updateCashAccount(manager.abCodeMap(manager.codeBIndexMap(codeB)).dUserId.get,
        CashAccount(Currency.Cny, amount, 0, 0))
    }

    case DoRequestTransfer(t, _, _) =>
      t.`type` match {
        case Withdrawal =>
          manager.updateCashAccount(t.userId, CashAccount(t.currency, -t.amount, 0, t.amount))
        case _ =>
      }

    case AdminConfirmTransferSuccess(t, _, _) =>
      succeededTransfer(t)

    case AdminConfirmTransferProcessed(t) =>
      succeededTransfer(t)

    case AdminConfirmTransferFailure(t, _) =>
      failedTransfer(t)

    case CryptoTransferSucceeded(txType, t, minerFee) => {
      t foreach { succeededTransfer(_) }
      if (txType != Deposit && txType != DepositHot) {
        minerFee foreach { substractMinerFee(t(0).currency, _) }
      }
    }

    case CryptoTransferResult(multiTransfers) => {
      //println(s"AccountProcessor got success accountTransfer => ${multiTransfers.toString}")
      multiTransfers.values foreach {
        transferWithFee =>
          transferWithFee.transfers.foreach {
            transfer =>
              transfer.status match {
                case TransferStatus.Succeeded => succeededTransfer(transfer)
                case TransferStatus.Failed => failedTransfer(transfer)
                case TransferStatus.Confirmed =>
                  if (logger != null) {
                    logger.error("Unexpected transferStatus" + transfer.toString)
                  }
                  succeededTransfer(transfer)
                case _ =>
                  if (logger != null) {
                    logger.error("Unexpected transferStatus" + transfer.toString)
                  }
                // case _ => logger.error("Unexpected transferStatus" + transfer.toString)
              }
          }
          val txType = transferWithFee.transfers(0).`type`
          if (txType != Deposit && txType != DepositHot) {
            transferWithFee.minerFee foreach (substractMinerFee(transferWithFee.transfers(0).currency, _))
          }
      }
    }

    case CryptoTransferFailed(t, _) => {
      failedTransfer(t)
    }

    case DoCancelTransfer(t) =>
      failedTransfer(t)

    case DoSubmitOrder(side: MarketSide, order) =>
      manager.updateCashAccount(order.userId, CashAccount(side.outCurrency, -order.quantity, order.quantity, 0))
      manager.setLastOrderId(order.id)

    case OrderSubmitted(originOrderInfo, txs) =>
      val side = originOrderInfo.side
      txs foreach { tx =>
        val (takerUpdate, makerUpdate, fees) = (tx.takerUpdate, tx.makerUpdate, tx.fees)
        manager.transferFundFromLocked(from = takerUpdate.userId, to = makerUpdate.userId, side.outCurrency, takerUpdate.outAmount)
        manager.transferFundFromLocked(from = makerUpdate.userId, to = takerUpdate.userId, side.inCurrency, makerUpdate.outAmount)
        refund(side.inCurrency, makerUpdate.current)

        tx.fees.getOrElse(Nil) foreach { f =>
          manager.transferFundFromAvailable(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
        }

      }
      val order = txs.lastOption.map(_.takerUpdate.current).getOrElse(originOrderInfo.order)
      refund(side.outCurrency, order)

    case OrderCancelled(side, order) =>
      manager.conditionalRefund(true)(side.outCurrency, order)

    case DoRequestPayment(payment) =>
      manager.setLastPaymentId(payment.id)
      if (payment.payer != NULL_USER_ID)
        manager.updateCashAccount(payment.payer, CashAccount(payment.currency, -payment.amount, 0, 0))
      if (payment.payee != NULL_USER_ID)
        manager.updateCashAccount(payment.payee, CashAccount(payment.currency, payment.amount, 0, 0))
  }

  private def succeededTransfer(t: AccountTransfer) {
    t.`type` match {
      case Deposit => depositAction(t)
      case DepositHot => depositAction(t)
      case Withdrawal =>
        t.fee match {
          case Some(f) if (f.amount > 0) =>
            manager.transferFundFromPendingWithdrawal(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
            manager.updateCashAccount(t.userId, CashAccount(t.currency, 0, 0, f.amount - t.amount))
          case _ =>
            manager.updateCashAccount(t.userId, CashAccount(t.currency, 0, 0, -t.amount))
        }
      case _ =>
        println("succeededTransfer type not match")
    }

    def depositAction(transfer: AccountTransfer) {
      manager.updateCashAccount(transfer.userId, CashAccount(transfer.currency, transfer.amount, 0, 0))
      transfer.fee match {
        case Some(f) if (f.amount > 0) =>
          manager.transferFundFromAvailable(f.payer, f.payee.getOrElse(COINPORT_UID), f.currency, f.amount)
        case _ => None
      }
    }
  }

  private def substractMinerFee(currency: Currency, minerFee: Long) {
    manager.updateCryptoAccount(CashAccount(currency, -minerFee, 0, 0))
  }

  private def failedTransfer(t: AccountTransfer) {
    t.`type` match {
      case Withdrawal => manager.updateCashAccount(t.userId, CashAccount(t.currency, t.amount, 0, -t.amount))
      case _ =>
    }
  }

  private def refund(currency: Currency, order: Order) = order.refund match {
    case Some(Refund(_, quantity)) if quantity > 0 =>
      manager.refund(order.userId, currency, quantity)
    case _ =>
  }
}

