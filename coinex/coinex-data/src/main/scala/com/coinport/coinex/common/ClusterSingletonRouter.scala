/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 */

package com.coinport.coinex.common

import akka.actor._

import akka.cluster._
import akka.cluster.ClusterEvent._
import scala.collection.immutable.SortedSet

class ClusterSingletonRouter(role: String, path: String) extends Actor {

  // subscribe to MemberEvent, re-subscribe when restart
  override def preStart() = Cluster(context.system).subscribe(self, classOf[MemberEvent])
  override def postStop() = Cluster(context.system).unsubscribe(self)

  // sort by age, oldest first
  implicit val ageOrdering = Ordering.fromLessThan[Member] { (a, b) => a.isOlderThan(b) }
  var membersByAge = SortedSet.empty[Member]

  def receive = {
    case state: CurrentClusterState =>
      membersByAge = SortedSet.empty ++ state.members.filter { m =>
        m.status == MemberStatus.Up && m.hasRole(role)
      }

    case MemberUp(m) => if (m.hasRole(role)) membersByAge += m
    case MemberRemoved(m, _) => if (m.hasRole(role)) membersByAge -= m
    case other => destination foreach { _.forward(other) }
  }

  def destination: Option[ActorSelection] =
    membersByAge.headOption map { m =>
      context.actorSelection(ActorPath.fromString(RootActorPath(m.address) + path))
    }
}