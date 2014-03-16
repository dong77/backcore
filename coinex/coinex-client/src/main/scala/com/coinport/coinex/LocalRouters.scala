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
import Implicits._

object LocalRouters {
  val USER_PROCESSOR = "user_processor"
  val ACCOUNT_PROCESSOR = "account_processor"
  val MARKET_UPDATE_PROCESSOR = "marke_update_processor"
  def MARKET_PROCESSOR(side: MarketSide) = "market_processor_" + side.asString

  val USER_VIEW = "user_view"
  val ACCOUNT_VIEW = "account_view"
  val USER_ORDERS_VIEW = "user_orders_view"
  val CANDLE_DATA_VIEW = "candle_data_view"
  def MARKET_DEPTH_VIEW(side: MarketSide) = "market_depth_view_" + side.asString
}

class LocalRouters(markets: Seq[MarketSide])(implicit system: ActorSystem) {

  def singletonRouter(name: String) = system.actorOf(
    Props(new ClusterSingletonRouter(name, "user/" + name + "/singleton")), name + "_router")

  def viewRouter(name: String) = system.actorOf(
    ClusterRouterGroup(RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/" + name),
        allowLocalRoutees = false,
        useRole = Some(name))).props,
    name + "_router")

  import LocalRouters._
  //---------------------------------------------------------------------------
  val userProcessor = singletonRouter(USER_PROCESSOR)
  val accountProcessor = singletonRouter(ACCOUNT_PROCESSOR)
  val marketUpdateProcessor = singletonRouter(MARKET_UPDATE_PROCESSOR)

  val marketProcessors = bidirection(Map(markets map { m =>
    m -> singletonRouter(MARKET_PROCESSOR(m))
  }: _*))

  val userView = viewRouter(USER_VIEW)
  val accountView = viewRouter(ACCOUNT_VIEW)
  val userOrdersView = viewRouter(USER_ORDERS_VIEW)
  val candleDataView = viewRouter(CANDLE_DATA_VIEW)

  val marketDepthViews = bidirection(Map(markets map { m =>
    m -> viewRouter(MARKET_DEPTH_VIEW(m))
  }: _*))

  private def bidirection(m: Map[MarketSide, ActorRef]): Map[MarketSide, ActorRef] = {
    m ++ m.map {
      case (side, v) => (side.reverse, v)
    }
  }
}