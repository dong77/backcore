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
}

class LocalRouters(markets: Seq[MarketSide])(implicit system: ActorSystem) {

  import LocalRouters._
  val userProcessor = routerForProcessor(USER_PROCESSOR)
  val accountProcessor = routerForProcessor(ACCOUNT_PROCESSOR)
  val marketUpdateProcessor = routerForProcessor(MARKET_UPDATE_PROCESSOR)

  val marketProcessors = bidirection(Map(markets map { m =>
    m -> routerForProcessor(MARKET_PROCESSOR(m))
  }: _*))

  val userView = routerForView(USER_VIEW)
  val accountView = routerForView(ACCOUNT_VIEW)
  val userOrdersView = routerForView(USER_ORDERS_VIEW)

  val candleDataView = bidirection(Map(markets map { m =>
    m -> routerForView(CANDLE_DATA_VIEW(m))
  }: _*))

  val marketDepthViews = bidirection(Map(markets map { m =>
    m -> routerForView(MARKET_DEPTH_VIEW(m))
  }: _*))

  private def routerForProcessor(name: String) = system.actorOf(
    ClusterSingletonProxy.defaultProps("/user/" + name + "/singleton", name),
    name + "_router")

  private def routerForView(name: String) = system.actorOf(
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