package com.coinport.exchange

import akka.persistence._
import akka.actor.ActorLogging
import scala.collection.mutable.{ Map => MMap }
import CurrencyType._

class BalanceProcessor3 extends EventsourcedProcessor with ActorLogging {

  val state = MMap[Long, MMap[CurrencyType, Account]]()

  def updateState(event: Event): Unit = event match {
    case DepositConfirmed(d: Deposit) =>
      val accounts = state.getOrElse(d.uid, MMap[CurrencyType, Account]())
      state += d.uid -> accounts

      val account = accounts.getOrElse(d.currency, Account())
      val a = account.copy(amount = account.amount + d.total)
      accounts += d.currency -> a

    //val account = state

    case o: LimitPriceSellOrder =>
    case o: LimitPriceBuyOrder =>
    case o: MarketPriceSellOrder =>
    case o: MarketPriceBuyOrder =>
    case _ =>
  }

  val receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: Any) =>
  }

  val receiveCommand: Receive = {
    case DoSubmitOrder(o: LimitPriceSellOrder) =>
      persist(OrderCreated(o))(updateState)
      sender ! OrderCreated(o)

    case DoSubmitOrder(o: LimitPriceBuyOrder) =>
      persist(OrderCreated(o))(updateState)
      sender ! OrderCreated(o)

    case DoSubmitOrder(o: MarketPriceSellOrder) =>
      persist(OrderCreated(o))(updateState)
      sender ! OrderCreated(o)

    case DoSubmitOrder(o: MarketPriceBuyOrder) =>
      persist(OrderCreated(o))(updateState)
      sender ! OrderCreated(o)

    case other =>
      log.debug(other.toString)
  }
}

case class Account(amount: Double = 0, pendingWithdrawl: Double = 0, pendingDeposit: Double = 0) {
  def total = amount + pendingWithdrawl
}