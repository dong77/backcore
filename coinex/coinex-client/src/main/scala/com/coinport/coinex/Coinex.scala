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
  private val ap = routers.accountProcessor
  private val av = routers.accountView
  private val ulp = routers.userLogsProcessor
  private val ulv = routers.userLogsView

  private val mps = routers.marketProcessors
  private val mvs = routers.marketViews

  def receive = {
    //-------------------------------------------------------------------------
    // Account Processor
    case m: DoDepositCash => ap forward Persistent(m)
    case m: DoRequestCashWithdrawal => ap forward Persistent(m)
    case m: DoConfirmCashWithdrawalSuccess => ap forward Persistent(m)
    case m: DoConfirmCashWithdrawalFailed => ap forward Persistent(m)
    case m: DoSubmitOrder => ap forward Persistent(m)
    // Market Processors
    case m @ DoCancelOrder(side, _) => mps(side) forward Persistent(m)
    // UserLogs Processor

    //-------------------------------------------------------------------------
    // Account View
    case m: QueryAccount => av forward m
    // Market Views
    case m @ QueryMarket(side, _) => mvs(side) forward m

    // UserLogs View
    case m: QueryUserLog => ulv forward m

    //-------------------------------------------------------------------------
    case Persistent => throw new IllegalArgumentException("Coinex class doesn't handle Persistent messages")
    case m => throw new IllegalArgumentException("Coinex class doesn't handle messages of type: " + m.getClass.getCanonicalName)
  }
}