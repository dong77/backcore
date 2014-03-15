/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.actor._
import akka.cluster.routing._
import akka.routing._
import com.coinport.coinex.common.ClusterSingletonRouter
import com.coinport.coinex.data._
import akka.persistence._
import Implicits._

final class Coinex(routers: LocalRouters) extends Actor {

  def receive = {
    //-------------------------------------------------------------------------
    // Account Processor
    case m: DoDepositCash => routers.accountProcessor forward Persistent(m)
    case m: DoRequestCashWithdrawal => routers.accountProcessor forward Persistent(m)
    case m: DoConfirmCashWithdrawalSuccess => routers.accountProcessor forward Persistent(m)
    case m: DoConfirmCashWithdrawalFailed => routers.accountProcessor forward Persistent(m)
    case m: DoSubmitOrder => routers.accountProcessor forward Persistent(m)

    // Market Processors
    case m @ DoCancelOrder(side, _) => routers.marketProcessors(side) forward Persistent(m)


    //-------------------------------------------------------------------------
    // AccountView
    case m: QueryAccount => routers.accountView forward m

    // MarketDepthViews
    case m @ QueryMarket(side, _) => routers.marketDepthViews(side) forward m

    // MarketUserLogsView
    case m: QueryUserOrders => routers.userLogsView forward m

    // CandleDataview
    case m: QueryMarketCandleData => routers.candleDataView forward m

    //-------------------------------------------------------------------------
    case Persistent => throw new IllegalArgumentException("Coinex doesn't handle persistent messages")
    case m => throw new IllegalArgumentException("Coinex doesn't handle messages of type: " + m.getClass.getCanonicalName)
  }
}