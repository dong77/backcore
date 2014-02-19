package com.coinport.coinex

import scala.concurrent.duration._
import akka.actor._
import akka.persistence._
import Domain._

object MarketView {
  private[MarketView] case class State(x: String = "")
}

class MarketView extends View with ActorLogging {
  import MarketView._
  override def processorId = "coinex_market_processor"
  println("--------------market view created:" + self.path)

  var state = State()

  def receive = {
    case p @ Persistent(payload, _) => println("view catch up event: " + payload)
    case _ =>
  }

}
