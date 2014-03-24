/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonManager
import akka.cluster.routing._
import akka.routing._
import org.slf4s.Logging

import com.coinport.coinex.accounts._
import com.coinport.coinex.data._
import com.coinport.coinex.markets._
import com.coinport.coinex.users._
import com.coinport.coinex.mail._
import Implicits._

class Deployer(markets: Seq[MarketSide])(implicit cluster: Cluster) extends Object with Logging {
  implicit val system = cluster.system

  def deploy(routers: LocalRouters) = {
    import LocalRouters._

    markets foreach { m =>
      val props = Props(new MarketProcessor(m, routers.accountProcessor.path, routers.marketUpdateProcessor.path))
      deployProcessor(props, MARKET_PROCESSOR(m))
      deployView(Props(new MarketDepthView(m)), MARKET_DEPTH_VIEW(m))
      deployView(Props(new CandleDataView(m)), CANDLE_DATA_VIEW(m))
    }

    deployProcessor(Props(new UserProcessor(routers.mailer)), USER_PROCESSOR)
    deployProcessor(Props(new AccountProcessor(routers.marketProcessors)), ACCOUNT_PROCESSOR)
    deployProcessor(Props(new MarketUpdateProcessor()), MARKET_UPDATE_PROCESSOR)

    deployView(Props(classOf[UserView]), USER_VIEW)
    deployView(Props(classOf[AccountView]), ACCOUNT_VIEW)
    deployView(Props(classOf[UserOrdersView]), USER_ORDERS_VIEW)

    deployMailer(MAILER)
  }

  private def deployProcessor(props: Props, name: String) =
    if (cluster.selfRoles.contains(name)) {
      log.debug("~" * 30 + " " + name + " is created")
      system.actorOf(ClusterSingletonManager.props(
        singletonProps = props,
        singletonName = "singleton",
        terminationMessage = PoisonPill,
        role = Some(name)),
        name = name)
    }

  private def deployView(props: Props, name: String) =
    if (cluster.selfRoles.contains(name)) {
      system.actorOf(props, name)
    }

  private def deployMailer(name: String) = {
    if (cluster.selfRoles.contains(name)) {
      val handler = new MandrillMailHandler()
      val props = Props(new Mailer(handler))
      system.actorOf(RoundRobinPool(8).props(props), name)
    }
  }
}