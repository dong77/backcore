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
  //---------------------------------------------------------------------------
  val userProcessor = system.actorOf(Props(new ClusterSingletonRouter("ap", "user/up/singleton")), "up_router")

  val userView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/uv"),
        allowLocalRoutees = false,
        useRole = Some("uv"))).props, "uv_router")

  //---------------------------------------------------------------------------
  val accountProcessor = system.actorOf(Props(new ClusterSingletonRouter("ap", "user/ap/singleton")), "ap_router")

  val accountView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/av"),
        allowLocalRoutees = false,
        useRole = Some("av"))).props, "av_router")

  //---------------------------------------------------------------------------
  val marketUpdateProcessoressor = system.actorOf(Props(new ClusterSingletonRouter("pmp", "user/pmp/singleton")), "pmp_router")

  val userLogsView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/mv_ul"),
        allowLocalRoutees = false,
        useRole = Some("mv_ul"))).props, "mv_ul_router")

  val candleDataView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/mv_cd"),
        allowLocalRoutees = false,
        useRole = Some("mv_cd"))).props, "mv_cd_router")

  //---------------------------------------------------------------------------
  val marketProcessors = bidirection(Map(markets map { m =>
    m -> system.actorOf(
      Props(new ClusterSingletonRouter("mp_" + m.asString, "/user/mp_" + m.asString + "/singleton")),
      "mp_" + m.asString + "_router")
  }: _*))

  val marketDepthViews = bidirection(Map(markets map { m =>
    m -> system.actorOf(
      ClusterRouterGroup(
        RoundRobinGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = Int.MaxValue,
          routeesPaths = List("/user/mdv_" + m.asString),
          allowLocalRoutees = false,
          useRole = Some("mdv_" + m.asString))).props, "mdv_" + m.asString + "_router")
  }: _*))

  private def bidirection(m: Map[MarketSide, ActorRef]): Map[MarketSide, ActorRef] = {
    m ++ m.map {
      case (side, v) => (side.reverse, v)
    }
  }
}