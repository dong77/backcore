package com.coinport.exchange.roles

import akka.actor._
import akka.cluster.ClusterEvent.RoleLeaderChanged

class MarketProcessingRoleActor extends Actor with ActorLogging {
  def receive = {
    case e: RoleLeaderChanged =>
      println("====role leader is: " + e.leader)
  }
}