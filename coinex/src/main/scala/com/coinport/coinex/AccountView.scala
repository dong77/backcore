package com.coinport.coinex

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import scala.collection.mutable
import Domain._

object AccountView {
  private[AccountView] class State {
    val userAccounts = mutable.Map.empty[UserId, mutable.Map[Currency, Account]]

    override def toString = {
      userAccounts.mkString("\n")
    }
  }
}

class AccountView extends View with ActorLogging {
  import AccountView._
  override def processorId = "coinex_account_processor"
  println("--------------account view created:" + self.path)
  var state = new State()

  def receive = {
    case DebugDump =>
      println(state.toString)

    case p @ Persistent(evt, _) =>
      println("view catch up event: " + evt)

      evt match {
        case DepositConfirmed(deposit) =>
          updateSpendable(deposit.uid, deposit.currency, _ + deposit.amount)

        case WithdrawalConfirmed(withdrawal) =>
          updateSpendable(withdrawal.uid, withdrawal.currency, _ - withdrawal.amount)

        case OrderSubmitted(o: BuyOrder) =>
        case OrderSubmitted(o: SellOrder) =>

        case msg =>
          log.warning("{} not supported by {}", msg, getClass().getName())
      }

    case _ =>

  }

  private def updateSpendable(uid: UserId, currency: Currency, update: Amount => Amount) = {
    val accounts = state.userAccounts.getOrElseUpdate(uid, mutable.Map.empty[Currency, Account])
    var account = accounts.getOrElse(currency, Account(currency))
    account = account.copy(spendable = update(account.spendable))
    if (account.spendable == 0) {
      accounts -= account.currency
      if (accounts.isEmpty) state.userAccounts -= uid
    } else {
      accounts += account.currency -> account
    }
  }
}
