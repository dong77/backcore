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
  def deploy(routers: LocalRouters) = {
    // User Processor (path: /user/up/singleton)
    if (cluster.selfRoles.contains("up")) {
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = Props(new UserProcessor()),
        singletonName = "singleton",
        terminationMessage = PoisonPill,
        role = Some("up")),
        name = "up")
    }

    // AccountView (path: /user/uv)
    if (cluster.selfRoles.contains("uv")) {
      system.actorOf(Props(classOf[UserView]), "uv")
    }

    // AccountProcessor (path: /user/ap/singleton)
    if (cluster.selfRoles.contains("ap")) {
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = Props(new AccountProcessor(routers.marketProcessors)),
        singletonName = "singleton",
        terminationMessage = PoisonPill,
        role = Some("ap")),
        name = "ap")
    }

    // Account View (path: /user/av)
    if (cluster.selfRoles.contains("av")) {
      system.actorOf(Props(classOf[AccountView]), "av")
    }

    // MarketUpdateProcessor (path: /user/mup)
    if (cluster.selfRoles.contains("mup")) {
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = Props(new MarketUpdateProcessor()),
        singletonName = "singleton",
        terminationMessage = PoisonPill,
        role = Some("mup")),
        name = "mup")
    }

    // MarketUpdateUserLogsView (path: /user/mu_ulv)
    if (cluster.selfRoles.contains("mu_ulv")) {
      system.actorOf(Props(classOf[MarketUpdateUserLogsView]), "mu_ulv")
    }

    // MarketUpdateCandleDataView (path: /user/mu_ulv)
    if (cluster.selfRoles.contains("mu_cdv")) {
      system.actorOf(Props(classOf[MarketUpdateCandleDataView]), "mu_cdv")
    }

    // Market Processors and Views
    markets foreach { m =>
      // path: /user/mp_btc_rmb/singleton
      if (cluster.selfRoles.contains("mp_" + m.asString)) {
        system.actorOf(ClusterSingletonManager.props(
          singletonProps = Props(new MarketProcessor(m, routers.accountProcessor.path, routers.userLogsProcessor.path)),
          singletonName = "singleton",
          terminationMessage = PoisonPill,
          role = Some("mp_" + m.asString)),
          name = "mp_" + m.asString)
      }

      // Market views (path: /user/mv_btc_rmb)
      if (cluster.selfRoles.contains("mdv_" + m.asString)) {
        system.actorOf(Props(new MarketDepthView(m)), "mdv_" + m.asString)
      }
    }
  }
}