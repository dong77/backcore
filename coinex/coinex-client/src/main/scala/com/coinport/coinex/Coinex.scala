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
      case m: DoRegisterUser => routers.userProcessor forward Persistent(m)
      case m: DoRequestPasswordReset => routers.userProcessor forward Persistent(m)
      case m: DoResetPassword => routers.userProcessor forward Persistent(m)
      case m: Login => routers.userProcessor forward m
      case m: ValidatePasswordResetToken => routers.userProcessor forward m

      //-------------------------------------------------------------------------
      // Account Processor
      case m: DoDepositCash => routers.accountProcessor forward Persistent(m)
      case m: DoRequestCashWithdrawal => routers.accountProcessor forward Persistent(m)
      case m: DoConfirmCashWithdrawalSuccess => routers.accountProcessor forward Persistent(m)
      case m: DoConfirmCashWithdrawalFailed => routers.accountProcessor forward Persistent(m)
      case m: DoSubmitOrder => routers.accountProcessor forward Persistent(m)

      // Market Processors
      case m @ DoCancelOrder(side, _, _) => routers.marketProcessors(side) forward Persistent(m)

      // Robot Processor
      case m: DoSubmitRobot => routers.robotProcessor forward Persistent(m)
      case m: DoCancelRobot => routers.robotProcessor forward Persistent(m)

      //-------------------------------------------------------------------------
      // AccountView
      case m: QueryAccount => routers.accountView forward m

      // MarketDepthViews
      case m @ QueryMarket(side, _) =>
        routers.marketDepthViews(side) forward m

      // UserOrdersView
      case m: QueryUserOrders => routers.userOrdersView forward m

      // CandleDataView
      case m @ QueryCandleData(side, _, _, _) => routers.candleDataView(side) forward m

      // Mailer
      case m: SendMailRequest =>
        routers.mailer forward m

      // RobotMetricsView
      case QueryRobotMetrics => routers.robotMetricsView forward QueryRobotMetrics

      // TransactionDataView
      case m @ QueryTransactionData(side, _, _) => routers.transactionDataView(side) forward m

      // UserTransactionView
      case m @ QueryUserTransaction(side, _, _, _, _) => routers.userTransactionView(side) forward m

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
