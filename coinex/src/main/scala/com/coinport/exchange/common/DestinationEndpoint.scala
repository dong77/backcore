package com.coinport.exchange.common

import akka.actor._
class DestinationEndpoint(destination: ActorRef) extends Actor {
  def receive = {
    case msg => destination forward msg
  }
}