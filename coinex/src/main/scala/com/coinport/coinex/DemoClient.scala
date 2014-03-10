/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import com.typesafe.config.ConfigFactory
import com.coinport.coinex.domain._
import akka.actor._
import akka.cluster.routing._
import akka.routing._
import akka.contrib.pattern._
import akka.persistence.Persistent
import akka.cluster.Cluster

object DemoClient extends App {
  val config = ConfigFactory.load("demo-client.conf")

  implicit val system = ActorSystem("coinex", config)
  val cluster = Cluster(system)
  // ------------------------------------------------------------------------------------------------
  // Cluster-aware Routers Deployment
  val accountProcessor = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/ap/singleton"),
        allowLocalRoutees = true,
        useRole = None)).props, "ap_router")

  val accountView = system.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = Int.MaxValue,
        routeesPaths = List("/user/av"),
        allowLocalRoutees = true,
        useRole = None)).props, "av_router")

  val markets = Seq(BTC ~> RMB) // TODO(d): read supported markets from configuration.
  val marketProcessors = Map(
    markets map { market =>
      market -> system.actorOf(
        ClusterRouterGroup(
          RoundRobinGroup(Nil),
          ClusterRouterGroupSettings(
            totalInstances = Int.MaxValue,
            routeesPaths = List("/user/mp_" + market + "/singleton"),
            allowLocalRoutees = true,
            useRole = None)).props, "mp_" + market + "_router")
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