package com.coinport.coinex.monitoring

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Terminated
import akka.actor.ActorLogging

class ActorWatcher(actors: List[ActorRef]) extends Actor with ActorLogging {

  override def preStart = {
    super.preStart()
    log.info("watch actors: " + actors.toString)
    actors foreach { actor => context.watch(actor) }
  }

  override def receive = {
    case Terminated(actor) => log.error("[ACTOR TERMINATED ERROR] >>>>>> " + actor.toString)
    case _ =>
  }

}