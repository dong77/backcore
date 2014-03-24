/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.actor._
import akka.cluster.routing._
import akka.routing._
import com.coinport.coinex.common.ClusterSingletonProxy
import com.coinport.coinex.data._
import Implicits._

object LocalRouters {
  val USER_PROCESSOR = "user_processor"
  val ACCOUNT_PROCESSOR = "account_processor"
  val MARKET_UPDATE_PROCESSOR = "marke_update_processor"
  def MARKET_PROCESSOR(side: MarketSide) = "market_processor_" + side.asString

  val USER_VIEW = "user_view"
  val ACCOUNT_VIEW = "account_view"
  val USER_ORDERS_VIEW = "user_orders_view"
  def CANDLE_DATA_VIEW(side: MarketSide) = "candle_data_view_" + side.asString
  def MARKET_DEPTH_VIEW(side: MarketSide) = "market_depth_view_" + side.asString
  def TRANSACTION_DATA_VIEW(side: MarketSide) = "transaction_data_view_" + side.asString

  def MAILER = "mailer"

  val ROBOT_METRICS_VIEW = "robot_metrics_view"
}

class LocalRouters(markets: Seq[MarketSide])(implicit system: ActorSystem) {

  import LocalRouters._
  val userProcessor = routerForSingleton(USER_PROCESSOR)
  val accountProcessor = routerForSingleton(ACCOUNT_PROCESSOR)
  val marketUpdateProcessor = routerForSingleton(MARKET_UPDATE_PROCESSOR)

  val marketProcessors = bidirection(Map(markets map { m =>
    m -> routerForSingleton(MARKET_PROCESSOR(m))
  }: _*))

  val userView = routerFor(USER_VIEW)
  val accountView = routerFor(ACCOUNT_VIEW)
  val userOrdersView = routerFor(USER_ORDERS_VIEW)

  val candleDataView = bidirection(Map(markets map { m =>
    m -> routerFor(CANDLE_DATA_VIEW(m))
  }: _*))

  val marketDepthViews = bidirection(Map(markets map { m =>
    m -> routerFor(MARKET_DEPTH_VIEW(m))
  }: _*))

  val transactionDataView = bidirection(Map(markets map { m =>
    m -> routerFor(TRANSACTION_DATA_VIEW(m))
  }: _*))

  val mailer = routerFor(MAILER)

  val robotMetricsView = routerFor(ROBOT_METRICS_VIEW)

  private def routerForSingleton(name: String) = system.actorOf(
    ClusterSingletonProxy.defaultProps("/user/" + name + "/singleton", name),
    name + "_router")

  private def routerFor(name: String) = system.actorOf(
    ClusterRouterGroup(RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/" + name),
        allowLocalRoutees = false,
        useRole = Some(name))).props,
    name + "_router")

  private def bidirection(m: Map[MarketSide, ActorRef]): Map[MarketSide, ActorRef] = {
    m ++ m.map {
      case (side, v) => (side.reverse, v)
    }
  }
}
