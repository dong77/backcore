package com.coinport.exchange.roles

import akka.actor._
import com.coinport.exchange.domain._
import com.coinport.exchange.domain.Commands._

class CommandDispatchingRoleActor extends Actor with ActorLogging {

  val balanceProcessor: ActorRef = null
  val depositWithdrawalProcessor: ActorRef = null

  def receive = {
    case cmd: DoCreateWithdrawal => balanceProcessor forward cmd
    case cmd: DoCreateOrder => balanceProcessor forward cmd

    case cmd: DoCreatePendingVirtualDeposit => depositWithdrawalProcessor forward cmd
    case cmd: DoCreatePendingFaitDeposit => depositWithdrawalProcessor forward cmd
    case cmd: DoConfirmFaitDeposit => depositWithdrawalProcessor forward cmd
    // case cmd : DoCreateAdminDeposit => depositWithdrawalProcessor forward cmd
    case cmd: DoCancelWithdrawal => depositWithdrawalProcessor forward cmd
    case cmd: DoConfirmWithdrawal => depositWithdrawalProcessor forward cmd
    case cmd: DoFailWithdrawal => depositWithdrawalProcessor forward cmd

  }
}