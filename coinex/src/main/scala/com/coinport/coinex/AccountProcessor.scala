package com.coinport.coinex
import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import scala.collection.mutable
import java.util.Random
import Domain._

/**
 * *
 *   //------------domain objects
 * case class Market(out: Currency, in: Currency)
 *
 * case class Account(currency: Currency, spendable: Amount = 0.0, locked: Amount = 0.0)
 * case class UserAccount(uid: UserId, accounts: Map[Currency, Account])
 *
 * case class Deposit(uid: UserId, currency: String, amount: Amount)
 * case class Withdrawal(uid: UserId, currency: String, amount: Amount)
 *
 * case class MarketSellOrder(uid: UserId, market: Market, outAmount: Amount)
 * case class MarketBuyOrder(uid: UserId, market: Market, outAmount: Amount)
 * case class LimitPriceSellOrder(id: OrderId, uid: UserId, market: Market, amountToSell: Amount, price: Amount) { def inAmount = amountToSell * price }
 * case class LimitPriceBuyOrder(id: OrderId, uid: UserId, market: Market, amountToBuy: Amount, price: Amount) { def outAmount = amountToBuy * price }
 *
 * case class Transfer(currency: Currency, from: UserId, to: UserId, amount: Amount)
 * case class SellTx(id: TxId, market: Market, price: Amount, amountSold: Amount, orderId: OrderId, partial: Boolean) { def inAmount = amountSold * price }
 * case class BuyTx(id: TxId, market: Market, price: Amount, amountBought: Amount, orderId: OrderId, partial: Boolean) { def outAmount = amountBought * price }
 *
 * //------------commands
 * sealed trait Cmd
 * case class DebugDump extends Cmd
 * case class DebugResetState extends Cmd
 *
 * case class DoDeposit(deposit: Deposit) extends Cmd
 * case class DoWithdrawal(withdrawal: Withdrawal) extends Cmd
 *
 * case class SubmitOrder(order: AnyRef) extends Cmd
 * case class CancelOrder(order: AnyRef) extends Cmd
 *
 * //------------command responses
 * case class DoDepositResult(deposit: Deposit)
 * case class DoWithdrawalResult(withdrawal: Withdrawal)
 * case class SubmitOrderResult(order: AnyRef)
 * case class CancelOrderResult(order: AnyRef)
 *
 * //------------events
 * sealed trait Evt
 * case class DepositConfirmed(deposit: Deposit) extends Evt
 * case class WithdrawalConfirmed(withdrawal: Withdrawal) extends Evt
 *
 * case class OrderSubmitted(order: AnyRef) extends Evt
 * case class OrderCancelled(order: AnyRef) extends Evt
 * case class OrderCancellationFailed(order: AnyRef, reason: String) extends Evt
 * case class TxConfirmed(sellTx: SellTx, buyTx: BuyTx, sellTransfer: Transfer, buyTransfer: Transfer) extends Evt
 * }
 */
object AccountProcessor {
  private[AccountProcessor] class State {
    val userAccounts = mutable.Map.empty[UserId, mutable.Map[Currency, Account]]

    override def toString = {
      userAccounts.mkString("\n")
    }
  }

}

class AccountProcessor(marketProcessorPath: ActorPath) extends EventsourcedProcessor with ActorLogging {
  import AccountProcessor._
  override def processorId = "coinex_account_processor"
  println("============account processor created: " + self.path)
  val channel = context.actorOf(Channel.props("coinex-ap2mp"), name = "ap2mp")

  var state = new State()

  override val receiveCommand: Receive = {
    case cmd =>
      log.info("--- CMD: {}", cmd)
      if (receiveCommandInternal.isDefinedAt(cmd)) receiveCommandInternal(cmd)
  }
  override val receiveRecover: Receive = {
    case event =>
      log.info("--- EVT: {}, lastSequenceNr: {}", event, lastSequenceNr)
      if (receiveRecoverInternal.isDefinedAt(event)) receiveRecoverInternal(event)
  }

  private def receiveRecoverInternal: Receive = {
    case SnapshotOffer(_, _) =>
    case evt: Evt => updateState(evt)
  }

  private def receiveCommandInternal: Receive = {
    case DebugDump =>
      println("-" * 100 + "\n" + state.toString)

    case DebugResetState =>
      state = new State()
      log.info("processor state reset")

    case DoDeposit(deposit) =>
      if (deposit.amount > 0) {
        persist(DepositConfirmed(deposit))(updateState)
      }
      sender ! DoDepositResult(deposit)

    case DoWithdrawal(withdrawal) =>
      if (withdrawal.amount > 0) {
        val spendable = getSpendable(withdrawal.uid, withdrawal.currency)
        if (spendable >= withdrawal.amount) {
          persist(WithdrawalConfirmed(withdrawal))(updateState)
          sender ! DoWithdrawalResult(withdrawal)
          log.warning("withdrawal done: {}", withdrawal)
        } else {
          log.warning("fund not enough: {}, withdrawal failed: {}", spendable, withdrawal)
        }
      }

    case SubmitOrder(o: BuyOrder) =>
      val spendable = getSpendable(o.uid, o.market.out)
      if (spendable >= o.payAmount) {
        persist(OrderSubmitted(o))(updateState)
        log.warning("order submitted: {}", o)
      } else {
        log.warning("fund not enough: {}, order not submitted: {}", spendable, o)
      }

    case SubmitOrder(o: SellOrder) =>
      val spendable = getSpendable(o.uid, o.market.in)
      if (spendable >= o.payAmount) {
        persist(OrderSubmitted(o))(updateState)
        log.warning("order submitted: {}", o)
      } else {
        log.warning("fund not enough: {}, order not submitted: {}", spendable, o)
      }

    case msg =>
      log.error("receiveCommand not supported: {}", msg)
  }

  def updateState(evt: Evt) = {
    evt match {
      case DepositConfirmed(deposit) =>
        updateSpendable(deposit.uid, deposit.currency, a => a.copy(spendable = a.spendable + deposit.amount))

      case WithdrawalConfirmed(withdrawal) =>
        updateSpendable(withdrawal.uid, withdrawal.currency, a => a.copy(spendable = a.spendable - withdrawal.amount))

      case evt @ OrderSubmitted(o: BuyOrder) =>
        println("ap: order submitted" + o)
        updateSpendable(o.uid, o.market.out, a => a.copy(spendable = a.spendable - o.payAmount, locked = a.locked + o.payAmount))
        channel forward Deliver(getCurrentPersistentMessage.withPayload(evt), marketProcessorPath)

      case evt @ OrderSubmitted(o: SellOrder) =>
        println("ap: order submitted" + o)
        updateSpendable(o.uid, o.market.in, a => a.copy(spendable = a.spendable - o.payAmount, locked = a.locked + o.payAmount))
        channel forward Deliver(getCurrentPersistentMessage.withPayload(evt), marketProcessorPath)

      case OrderCancelled(o: BuyOrder) =>
        updateSpendable(o.uid, o.market.out, a => a.copy(spendable = a.spendable + o.payAmount, locked = a.locked - o.payAmount))

      case OrderCancelled(o: SellOrder) =>
        updateSpendable(o.uid, o.market.in, a => a.copy(spendable = a.spendable + o.payAmount, locked = a.locked - o.payAmount))

      case msg =>
        log.error("updateState not supported: {}", msg)
    }

  }

  private def updateSpendable(uid: UserId, currency: Currency, update: Account => Account) = {
    val accounts = state.userAccounts.getOrElseUpdate(uid, mutable.Map.empty[Currency, Account])
    var account = accounts.getOrElse(currency, Account(currency))
    account = update(account)
    if (account.spendable == 0) {
      accounts -= account.currency
      if (accounts.isEmpty) state.userAccounts -= uid
    } else {
      accounts += account.currency -> account
    }
  }

  private def getSpendable(uid: UserId, currency: Currency) = {
    val accounts = state.userAccounts.getOrElse(uid, mutable.Map.empty[Currency, Account])
    var account = accounts.getOrElse(currency, Account(currency))
    account.spendable
  }
}
