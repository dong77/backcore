package com.coinport.exchange.roleplay

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.contrib.pattern.ClusterReceptionistExtension

import com.coinport.exchange.common._

class RolePlay(settings: RolePlaySettings) extends Actor with ActorLogging {

  case class Entry(actor: ActorRef, leader: Boolean = false)

  val cluster = Cluster(context.system)
  var roleActors = Map.empty[String, Entry]

  override def preStart() = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[ClusterDomainEvent], classOf[UnreachableMember])
  }
  override def postStop() = {
    cluster.unsubscribe(self)
    roleActors = Map.empty[String, Entry]
  }

  def receive = {
    case e: RoleLeaderChanged if cluster.getSelfRoles.contains(e.role) =>
      log.debug("role leader changed: {}", e)
      if (e.leader == Some(cluster.selfAddress)) {
        playLeaderRole(e)
      } else {
        playNonLeaderRole(e)
      }

      log.info("\n" + "=" * 80 + "\nRole Actors" + roleActors.mkString("\n", "\n", "\n") + "=" * 80)
      roleActors.values.foreach(_.actor ! e)

    case e: RoleLeaderChanged =>
      roleActors.values.foreach(_.actor ! e)
  }

  private def playNonLeaderRole(e: RoleLeaderChanged) = {
    val role = e.role
    val rs = getRoleSetting(role)
    def initActor = {
      val actor = context.actorOf(rs.props, role)
      roleActors += role -> Entry(actor, false)
      log.info("non-leader actor for role `{}` initialized: {}", role, actor)

      if (rs.registerAsService) {
        ClusterReceptionistExtension(context.system).registerService(actor)
        log.info("actor for role `{}` registered as a service: {}", role, actor)
      }
    }
    roleActors.get(role) match {
      case None => initActor
      case Some(entry) =>
        if (entry.leader && rs.leaderProps.isDefined) {
          log.info("leader actor for role `{}` stoped: {}", role, entry.actor)
          context.stop(entry.actor)
          initActor
        }
    }
  }

  private def playLeaderRole(e: RoleLeaderChanged) = {
    val role = e.role
    val rs = getRoleSetting(role)
    def initActor = {
      val actor = context.actorOf(rs.getLeaderProps, role)
      roleActors += role -> Entry(actor, true)
      log.info("leader actor for role `{}` initialized: {}", role, actor)

      if (rs.registerAsService) {
        ClusterReceptionistExtension(context.system).registerService(actor)
        log.info("actor for role `{}` registered as a service: {}", role, actor)
      }
    }
    roleActors.get(role) match {
      case None => initActor
      case Some(entry) =>
        if (!entry.leader && rs.leaderProps.isDefined) {
          log.info("non-leader actor for role `{}` stoped: {}", role, entry.actor)
          context.stop(entry.actor)
          initActor
        }
    }

  }

  private def getRoleSetting(role: String): RoleSetting = {
    def getDefaultRoleSetting(role: String) = {
      val default = Props(new DefaultRoleActor(role))
      RoleSetting(default, Some(default))
    }
    settings.propsMap.getOrElse(role, getDefaultRoleSetting(role))
  }
  private class DefaultRoleActor(role: String) extends Actor with ActorLogging {
    log.warning("Role `{}` not configed with any actor props, using DefaultRoleActor", role)
    def receive = { case _ => }
  }
}
