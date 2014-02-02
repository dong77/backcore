package com.coinport.exchange.common

import akka.actor._

class DestinationProxy(destinationPath: String) extends Actor {
  val destinationSelection: ActorSelection = context.actorSelection(destinationPath)

  def receive = {
    case msg =>
      println("-------proxy: " + msg)
      destinationSelection tell (msg, sender) // forward
  }
}