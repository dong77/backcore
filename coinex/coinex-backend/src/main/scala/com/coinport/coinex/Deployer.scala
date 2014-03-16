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
import akka.contrib.pattern.ClusterSingletonManager
import com.coinport.coinex.users._
import com.coinport.coinex.accounts._
import com.coinport.coinex.markets._
import akka.cluster.Cluster
import Implicits._

class Deployer(markets: Seq[MarketSide])(implicit cluster: Cluster) {
  val system = cluster.system

  def deployProcessor(props: Props, name: String) =
    if (cluster.selfRoles.contains(name)) {
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = props,
        singletonName = "singleton",
        terminationMessage = PoisonPill,
        role = Some(name)),
        name = name)
    }

  def deployView(props: Props, name: String) =
    if (cluster.selfRoles.contains(name)) {
      system.actorOf(props, name)
    }

  def deploy(routers: LocalRouters) = {
    import LocalRouters._

    markets foreach { m =>
      val props = Props(new MarketProcessor(m, routers.accountProcessor.path, routers.marketUpdateProcessor.path))
      deployProcessor(props, MARKET_PROCESSOR(m))
      deployProcessor(Props(new MarketDepthView(m)), MARKET_DEPTH_VIEW(m))
    }

    deployProcessor(Props(new UserProcessor()), USER_PROCESSOR)
    deployProcessor(Props(new AccountProcessor(routers.marketProcessors)), ACCOUNT_PROCESSOR)
    deployProcessor(Props(new MarketUpdateProcessor()), MARKET_UPDATE_PROCESSOR)

    deployView(Props(classOf[UserView]), USER_VIEW)
    deployView(Props(classOf[AccountView]), ACCOUNT_VIEW)
    deployView(Props(classOf[UserOrdersView]), USER_ORDERS_VIEW)
    deployView(Props(classOf[MarketCandleDataView]), CANDLE_DATA_VIEW)
  }
}