package com.coinport.exchange.views

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import akka.cluster.ClusterEvent.RoleLeaderChanged

class MarkethubAdminView extends View with ActorLogging {
  override def processorId = "markethub_processor"

  def receive = {
    case p @ Persistent(payload, _) =>
      log.info("------view sees payload: " + payload.toString)
  }
}