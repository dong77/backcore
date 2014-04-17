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
import org.slf4s.Logging

final class Coinex(routers: LocalRouters) extends Actor with Logging {

  def receive = {
    LoggingReceive {
      //-------------------------------------------------------------------------
      // User Proceessor
      case m: DoRegisterUser => routers.userProcessor forward m
      case m: DoRequestPasswordReset => routers.userProcessor forward m
      case m: DoResetPassword => routers.userProcessor forward m
      case m: Login => routers.userView forward m
      case m: ValidatePasswordResetToken => routers.userView forward m
      case m: VerifyGoogleAuthCode => routers.userView forward m

      //-------------------------------------------------------------------------
      // Account Processor
      case m: DoRequestCashDeposit => routers.accountProcessor forward m
      case m: DoRequestCashWithdrawal => routers.accountProcessor forward m
      case m: DoSubmitOrder => routers.accountProcessor forward m

      // Market Processors
      case m: DoCancelOrder => routers.marketProcessors(m.side) forward m

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
      case m: QueryMarketDepth => routers.marketDepthViews(m.side) forward m

      // CandleDataView
      case m: QueryCandleData => routers.candleDataView(m.side) forward m

      // Mailer
      case m: DoSendEmail => routers.mailer forward m

      // MetricsView
      case QueryMetrics => routers.metricsView forward QueryMetrics

      // Misc Queries
      case m: QueryTransaction => routers.transactionReader forward m
      case m: QueryOrder => routers.orderReader forward m
      case m: QueryDW => routers.depositWithdrawReader forward m

      // ApiAuthProcessor and View
      case m: DoAddNewApiSecret => routers.apiAuthProcessor forward Persistent(m)
      case m: DoDeleteApiSecret => routers.apiAuthProcessor forward Persistent(m)
      case m: QueryApiSecrets => routers.apiAuthView forward m

      // User Asset
      case m: QueryAsset => routers.assetView forward m

      case m @ QueryExportToMongoState(ExportedEventType.AccountEvent) => routers.dwProcessorEventExporter forward m
      case m @ QueryExportToMongoState(ExportedEventType.MarketEvent) => routers.marketUpdateProcessorEventExporter forward m

      //-------------------------------------------------------------------------
      case m =>
        log.error("Coinex received unsupported event: " + m.toString)
        sender ! MessageNotSupported(m.toString)
    }
  }
}
