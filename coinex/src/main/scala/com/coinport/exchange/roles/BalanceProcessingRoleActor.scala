package com.coinport.exchange.roles

import akka.actor._
import akka.cluster.ClusterEvent.RoleLeaderChanged

class BalanceProcessingRoleActor extends Actor with ActorLogging {
  def receive = {
    case e: RoleLeaderChanged =>
      println("====role leader is: " + e.leader)
  }
}