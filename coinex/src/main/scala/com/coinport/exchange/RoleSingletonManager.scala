package com.coinport.exchange

import akka.actor._
import akka.cluster.ClusterEvent.RoleLeaderChanged
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.coinport.exchange.actors.LocalRouters

/**
 * This actor guarantees that only one instance (singleton) of the target actor class will be created among
 * all nodes tagged with `role` in a cluster.
 *
 * Internally the hosting node will be the role leader. If the leader is down, RoleSingletonManager will start
 * another singleton actor in the newly elected role leader.
 */
class RoleSingletonManager(role: String, props: Props) extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  var actor: ActorRef = null

  override def preStart() = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[RoleLeaderChanged])
  }

  override def postStop() = {
    cluster.unsubscribe(self)
    actor = null
  }

  def receive = {
    case e: RoleLeaderChanged =>
      if (e.role == role) {
        val isLeader = e.leader == Some(cluster.selfAddress)
        if (isLeader) {
          log.info(s"I'm role leader for $role")
        }
        if (isLeader && actor == null) {
          actor = context.actorOf(props, "singleton")
        } else if (!isLeader && actor != null) {
          context.stop(actor)
          actor = null
        }
      }
    case _ =>
  }
}