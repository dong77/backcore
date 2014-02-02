package com.coinport.exchange

import Predef._
import akka.persistence._
import akka.actor.ActorLogging

class MarketProcessor(market: Market) extends EventsourcedProcessor with ActorLogging {

  def updateState(event: Event) = {

  }

  val receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: Any) =>
  }

  val receiveCommand: Receive = {
    case DoSubmitOrder(lpo: LimitPriceSellOrder) =>
      persist(OrderCreated(lpo))(updateState)
      sender ! OrderCreated(lpo)

    case DoCancelOrder(id: Long) =>

    case PersistenceFailure(payload, sequenceNr, cause) =>
    // message failed to be written to journal

    case other =>
    // message not written to journal

  }
}