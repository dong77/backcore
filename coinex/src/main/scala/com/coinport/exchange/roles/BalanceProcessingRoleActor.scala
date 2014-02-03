package com.coinport.exchange.roles

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import akka.cluster.ClusterEvent.RoleLeaderChanged

class BalanceProcessingRoleActor extends Actor with ActorLogging {

  val view = context.actorOf(Props(classOf[BalanceView]))
  def receive = {
    case e: RoleLeaderChanged =>
      println("====role leader is: " + e.leader)
  }
}

class BalanceView extends View with ActorLogging {
  override def processorId = Ids.BALANCE_TO_MARKET_CHANNEL("1")

  def receive = {
    case p @ Persistent(payload, _) =>
      log.info("------view sees payload: " + payload.toString)
  }
}
