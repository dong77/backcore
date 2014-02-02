package com.coinport.exchange

import akka.actor._

class Router(balanceProcessor: ActorRef, marketProcessorMap: Map[Market, ActorRef]) extends Actor {

  def receive = {
    case cmd: DoSubmitOrder => balanceProcessor forward cmd
    case cmd: DoCancelOrder =>
  }
}