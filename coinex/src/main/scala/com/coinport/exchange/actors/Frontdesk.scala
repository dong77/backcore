package com.coinport.exchange.actors

import akka.actor._
import com.coinport.exchange.domain._
import com.coinport.exchange.domain.Commands._
import scala.concurrent.duration._

case class LocalRouters(
  balanceProcessor: ActorRef,
  balanceView: ActorRef,
  adminBalanceView: ActorRef,
  transferProcessor: ActorRef,
  transferView: ActorRef,
  adminTransferView: ActorRef,
  markethubProcessor: ActorRef,
  markethubView: ActorRef)

class Frontdesk extends Actor with ActorLogging {
  var routers: LocalRouters = null

  def receive = {
    case msg: LocalRouters =>
      routers = msg
      context become ready
    case msg =>
      log.warning("Frontdesk not ready yet")
  }

  def ready: Receive = {
    case cmd @ (
      DoCreateWithdrawal |
      DoCreateOrder
      ) => routers.balanceProcessor forward cmd

    case cmd @ (
      DoCreatePendingVirtualDeposit |
      DoCreatePendingFaitDeposit |
      DoConfirmFaitDeposit |
      DoCancelWithdrawal |
      DoConfirmWithdrawal |
      // DoCreateAdminDeposit |
      DoFailWithdrawal
      ) => routers.markethubProcessor forward cmd
  }

}