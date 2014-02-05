package com.coinport.exchange.actors

import akka.actor._
import com.coinport.exchange.domain._
import com.coinport.exchange.domain.Commands._
import scala.concurrent.duration._

case class LocalRouters(
  balanceProcessor: ActorRef,
  balanceView: ActorRef,
  balanceAdminView: ActorRef,
  transferProcessor: ActorRef,
  transferView: ActorRef,
  transferAdminView: ActorRef,
  markethubProcessor: ActorRef,
  markethubView: ActorRef,
  markethubAdminView: ActorRef)

class Frontdesk(routers: LocalRouters) extends Actor with ActorLogging {

  def receive = {
    case cmd @ (
      DoCreateWithdrawal |
      DoCreateOrder
      ) => routers.balanceProcessor forward cmd

    case cmd: DoCreatePendingDeposit =>
      routers.transferProcessor forward cmd

    case cmd @ (
      DoConfirmDeposit |
      DoCancelWithdrawal |
      DoConfirmWithdrawal |
      DoFailWithdrawal
      ) =>
      routers.transferProcessor forward cmd
  }
}