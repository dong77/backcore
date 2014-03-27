/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.actor._
import akka.cluster.routing._
import akka.routing._
import akka.contrib.pattern.ClusterSingletonProxy
import com.coinport.coinex.data._
import Implicits._
import akka.cluster.Cluster

object LocalRouters {
  val USER_PROCESSOR = "user_processor"
  val ACCOUNT_PROCESSOR = "account_processor"
  val MARKET_UPDATE_PROCESSOR = "marke_update_processor"
  val API_AUTH_PROCESSOR = "api_auth_processor"
  def MARKET_PROCESSOR(side: MarketSide) = "market_processor_" + side.asString

  val USER_VIEW = "user_view"
  val ACCOUNT_VIEW = "account_view"
  val USER_ORDERS_VIEW = "user_orders_view"
  val ROBOT_METRICS_VIEW = "robot_metrics_view"
  val API_AUTH_VIEW = "api_auth_view"

  def CANDLE_DATA_VIEW(side: MarketSide) = "candle_data_view_" + side.asString
  def MARKET_DEPTH_VIEW(side: MarketSide) = "market_depth_view_" + side.asString
  def TRANSACTION_DATA_VIEW(side: MarketSide) = "transaction_data_view_" + side.asString
  def USER_TRANSACTION_VIEW(side: MarketSide) = "user_transaction_view_" + side.asString

  def MAILER = "mailer"

}

class LocalRouters(markets: Seq[MarketSide])(implicit cluster: Cluster) {
  implicit val system = cluster.system

  import LocalRouters._
  val userProcessor = routerForSingleton(USER_PROCESSOR)
  val accountProcessor = routerForSingleton(ACCOUNT_PROCESSOR)
  val marketUpdateProcessor = routerForSingleton(MARKET_UPDATE_PROCESSOR)
  val apiAuthProcessor = routerForSingleton(API_AUTH_PROCESSOR)

  val marketProcessors = bidirection(Map(markets map { m =>
    m -> routerForSingleton(MARKET_PROCESSOR(m))
  }: _*))

  val userView = routerFor(USER_VIEW)
  val accountView = routerFor(ACCOUNT_VIEW)
  val userOrdersView = routerFor(USER_ORDERS_VIEW)
  val apiAuthView = routerFor(API_AUTH_VIEW)

  val candleDataView = bidirection(Map(markets map { m =>
    m -> routerFor(CANDLE_DATA_VIEW(m))
  }: _*))

  val marketDepthViews = bidirection(Map(markets map { m =>
    m -> routerFor(MARKET_DEPTH_VIEW(m))
  }: _*))

  val transactionDataView = bidirection(Map(markets map { m =>
    m -> routerFor(TRANSACTION_DATA_VIEW(m))
  }: _*))

  val userTransactionView = bidirection(Map(markets map { m =>
    m -> routerFor(USER_TRANSACTION_VIEW(m))
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
        allowLocalRoutees = cluster.selfRoles.contains(name),
        useRole = Some(name))).props,
    name + "_router")

  private def bidirection(m: Map[MarketSide, ActorRef]): Map[MarketSide, ActorRef] = {
    m ++ m.map {
      case (side, v) => (side.reverse, v)
    }
  }
}