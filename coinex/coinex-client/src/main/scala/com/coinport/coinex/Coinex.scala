/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.actor._
import akka.event.LoggingReceive
import akka.cluster.routing._
import akka.routing._
import com.coinport.coinex.data._
import akka.persistence._
import Implicits._

final class Coinex(routers: LocalRouters) extends Actor {

  def receive = {
    LoggingReceive {
      //-------------------------------------------------------------------------
      // User Proceessor
      case m: DoRegisterUser => routers.userProcessor forward m
      case m: DoRequestPasswordReset => routers.userProcessor forward m
      case m: DoResetPassword => routers.userProcessor forward m
      case m: Login => routers.userProcessor forward m
      case m: ValidatePasswordResetToken => routers.userProcessor forward m

      //-------------------------------------------------------------------------
      // Account Processor
      case m: DoRequestCashDeposit => routers.accountProcessor forward m
      case m: DoRequestCashWithdrawal => routers.accountProcessor forward m
      case m: DoSubmitOrder => routers.accountProcessor forward m

      // Market Processors
      case m @ DoCancelOrder(side, _, _) => routers.marketProcessors(side) forward Persistent(m)

      // Robot Processor
      case m: DoSubmitRobot => routers.robotProcessor forward Persistent(m)
      case m: DoCancelRobot => routers.robotProcessor forward Persistent(m)

      // DepoistWithdraw Processor
      case m: AdminConfirmCashDepositFailure => routers.depositWithdrawProcessor forward m
      case m: AdminConfirmCashDepositSuccess => routers.depositWithdrawProcessor forward m
      case m: AdminConfirmCashWithdrawalFailure => routers.depositWithdrawProcessor forward m
      case m: AdminConfirmCashWithdrawalSuccess => routers.depositWithdrawProcessor forward m

      //-------------------------------------------------------------------------
      // AccountView
      case m: QueryAccount => routers.accountView forward m

      // MarketDepthViews
      case m @ QueryMarketDepth(side, _) =>
        routers.marketDepthViews(side) forward m

      // CandleDataView
      case m @ QueryCandleData(side, _, _, _) => routers.candleDataView(side) forward m

      // Mailer
      case m: DoSendEmail =>
        routers.mailer forward m

      // MetricsView
      case QueryMetrics => routers.robotMetricsView forward QueryMetrics

      // TransactionDataView
      case m @ QueryTransaction(side, _, _, _, _, _, _) => routers.transactionView(side) forward m

      // TransactionDataView
      case m @ QueryOrder(side, _, _, _, _, _, _) => routers.orderView(side) forward m

      // ApiAuthProcessor and View
      case m: DoAddNewApiSecret => routers.apiAuthProcessor forward Persistent(m)
      case m: DoDeleteApiSecret => routers.apiAuthProcessor forward Persistent(m)
      case m: QueryApiSecrets => routers.apiAuthView forward m

      //-------------------------------------------------------------------------
      case Persistent => throw new IllegalArgumentException("Coinex doesn't handle persistent messages")
      case m => throw new IllegalArgumentException("Coinex doesn't handle messages of type: " + m.getClass.getCanonicalName)
    }
  }
}
