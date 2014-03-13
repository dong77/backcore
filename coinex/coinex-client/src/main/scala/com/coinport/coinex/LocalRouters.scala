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

class LocalRouters(markets: Seq[MarketSide])(implicit system: ActorSystem) {
  val userProcessor = system.actorOf(Props(new ClusterSingletonRouter("ap", "user/up/singleton")), "up_router")

  val userView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/uv"),
        allowLocalRoutees = false,
        useRole = Some("uv"))).props, "uv_router")

  val accountProcessor = system.actorOf(Props(new ClusterSingletonRouter("ap", "user/ap/singleton")), "ap_router")

  val accountView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/av"),
        allowLocalRoutees = false,
        useRole = Some("av"))).props, "av_router")

  val marketProcessors = Map(markets map { m =>
    m -> system.actorOf(
      Props(new ClusterSingletonRouter("mp_" + m.asString, "/user/mp_" + m.asString + "/singleton")),
      "mp_" + m.asString + "_router")
  }: _*)

  val marketViews = Map(markets map { m =>
    m -> system.actorOf(
      ClusterRouterGroup(
        RoundRobinGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = Int.MaxValue,
          routeesPaths = List("/user/mv_" + m.asString),
          allowLocalRoutees = false,
          useRole = Some("mv_" + m.asString))).props, "mv_" + m.asString + "_router")
  }: _*)

  val userLogsProcessor = system.actorOf(Props(new ClusterSingletonRouter("ulp", "user/ulp/singleton")), "ulp_router")
}