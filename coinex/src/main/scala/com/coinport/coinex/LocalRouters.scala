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

class LocalRouters(markets: Seq[MarketSide])(implicit system: ActorSystem) {
  val accountProcessor = system.actorOf(Props(new ClusterSingletonRouter("ap", "user/ap/singleton")), "ap_router")

  val accountView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/av"),
        allowLocalRoutees = true,
        useRole = None)).props, "av_router")

  val marketProcessors = Map(
    markets map { m =>
      m -> system.actorOf(
        Props(new ClusterSingletonRouter("mp_" + m, "/user/mp_" + m + "/singleton")),
        "mp_" + m + "_router")
    }: _*)

  val marketViews = markets map { market =>
    market -> system.actorOf(
      ClusterRouterGroup(
        RoundRobinGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = Int.MaxValue,
          routeesPaths = List("/user/mv_" + market),
          allowLocalRoutees = true,
          useRole = None)).props, "mv_" + market + "_router")
  }

}